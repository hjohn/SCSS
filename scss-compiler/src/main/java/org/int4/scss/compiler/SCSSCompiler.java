package org.int4.scss.compiler;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A SCSS compiler which can compile SCSS files.
 */
public class SCSSCompiler {
  private enum OperatingSystem { WINDOWS, LINUX, MAC }

  private static final Logger LOGGER = System.getLogger(SCSSCompiler.class.getName());
  private static final Path TEMP_DIRECTORY;
  private static final OperatingSystem OS = getOS();
  private static final ThreadFactory FACTORY = Thread.ofVirtual().factory();
  private static final Executor EXECUTOR = r -> FACTORY.newThread(r).start();
  private static final Consumer<List<String>> DEFAULT_ERRORS_HANDLER = list -> {
    if(!list.isEmpty()) {
      throw new SCSSProcessingException(list.stream().collect(Collectors.joining()));
    }
  };
  private static final Consumer<List<String>> DEFAULT_WARNINGS_HANDLER = list -> list.stream().forEach(msg -> LOGGER.log(Level.WARNING, msg));
  private static final Consumer<List<String>> DEFAULT_DEPRECATIONS_HANDLER = list -> list.stream().forEach(msg -> LOGGER.log(Level.INFO, msg));

  private final Path root;
  private final Consumer<List<String>> errorsHandler;
  private final Consumer<List<String>> warningsHandler;
  private final Consumer<List<String>> deprecationsHandler;

  static {
    Path tempDirectory = null;
    String platform = OS.toString().toLowerCase() + "-" + System.getProperty("os.arch").toLowerCase();

    try {
      tempDirectory = Files.createTempDirectory("org.int4.scss-");

      Path shutdownHookTempDirectory = tempDirectory;

      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        try {
          deleteDirectoryRecursively(shutdownHookTempDirectory);
        }
        catch(IOException e) {
          e.printStackTrace();
        }
      }));

      try(InputStream is = SCSSCompiler.class.getResourceAsStream("/dart-sass-" + platform + ".zip")) {
        if(is == null) {
          throw new IllegalStateException("Unsupported platform: " + platform);
        }

        try(ZipInputStream zis = new ZipInputStream(is)) {
          for(;;) {
            ZipEntry entry = zis.getNextEntry();

            if(entry == null) {
              break;
            }

            Path entryPath = tempDirectory.resolve(entry.getName());

            if(entry.isDirectory()) {
              Files.createDirectories(entryPath); // Create the directory if entry is a folder
            }
            else {
              Files.copy(zis, entryPath);
            }

            zis.closeEntry();
          }
        }
      }
    }
    catch(IOException e) {
      throw new IllegalStateException("Unable to initialize Dart SCSS compiler for platform: " + platform, e);
    }

    TEMP_DIRECTORY = tempDirectory;
  }

  /**
   * Compiles the given scss file using the given root. This is a convenience
   * method for {@code SCSSCompiler.of(root).asString(scss)}.
   *
   * @param root a root for resolving imports against, cannot be {@code null}
   * @param scss a SCSS file to compile, cannot be {@code null}
   * @return a CSS string, never {@code null}
   * @throws IOException when an IO error occurred
   * @throws SCSSProcessingException when a compilation or syntax error is detected
   * @throws NullPointerException when any argument is {@code null}
   */
  public static String compile(Path root, Path scss) throws IOException {
    return of(root).asString(scss);
  }

  /**
   * Creates a reusable {@link SCSSCompiler}, which will throw an {@link SCSSProcessingException}
   * when any errors are encountered, will log warnings at level {@link Level#WARNING} and will log
   * deprecations on level {@link Level#INFO}.
   *
   * @param root a root for resolving imports against, cannot be {@code null}
   * @return a {@link SCSSCompiler} instance, never {@code null}
   * @throws NullPointerException when any argument is {@code null}
   */
  public static SCSSCompiler of(Path root) {
    return new SCSSCompiler(root, null, null, null);
  }

  /**
   * Creates a reusable {@link SCSSCompiler}, which will use the given handlers to
   * handle errors, warnings and deprecations. Setting any handler to {@code null}
   * will result in the corresponding default handler being used. The default handlers will throw an
   * {@link SCSSProcessingException} when any errors are encountered, will log warnings
   * at level {@link Level#WARNING} and will log deprecations on level {@link Level#INFO}.
   *
   * @param root a root for resolving imports against, cannot be {@code null}
   * @param errorsHandler a handler for errors encountered, can be {@code null}
   * @param warningsHandler a handler for warnings encountered, can be {@code null}
   * @param deprecationsHandler a handler for deprecations encountered, can be {@code null}
   * @return a {@link SCSSCompiler} instance, never {@code null}
   * @throws NullPointerException when any argument is {@code null}
   */
  public static SCSSCompiler of(Path root, Consumer<List<String>> errorsHandler, Consumer<List<String>> warningsHandler, Consumer<List<String>> deprecationsHandler) {
    return new SCSSCompiler(root, errorsHandler, warningsHandler, deprecationsHandler);
  }

  SCSSCompiler(Path root, Consumer<List<String>> errorsHandler, Consumer<List<String>> warningsHandler, Consumer<List<String>> deprecationsHandler) {
    this.root = Objects.requireNonNull(root, "root");
    this.errorsHandler = errorsHandler == null ? DEFAULT_ERRORS_HANDLER : errorsHandler;
    this.warningsHandler = warningsHandler == null ? DEFAULT_WARNINGS_HANDLER : warningsHandler;
    this.deprecationsHandler = deprecationsHandler == null ? DEFAULT_DEPRECATIONS_HANDLER : deprecationsHandler;
  }

  /**
   * Compiles the given scss file to a CSS string.
   *
   * @param scss a SCSS file to compile, cannot be {@code null}
   * @return a CSS string, never {@code null}
   * @throws IOException when an IO error occurred
   * @throws SCSSProcessingException when a compilation or syntax error is detected
   * @throws NullPointerException when any argument is {@code null}
   */
  public String asString(Path scss) throws IOException {
    MessageConsumer messageConsumer = new MessageConsumer();

    try(InputStream stdout = asStream(scss, messageConsumer)) {
      String output = new String(stdout.readAllBytes(), StandardCharsets.UTF_8);

      messageConsumer.callHandlers();

      return output;
    }
  }

  /**
   * Compiles the given scss file into a {@link URI}. The URI includes the CSS
   * as base64 encoded text.
   *
   * @param scss a SCSS file to compile, cannot be {@code null}
   * @return a {@link URI} containing the base64 encoded CSS, never {@code null}
   * @throws IOException when an IO error occurred
   * @throws SCSSProcessingException when a compilation or syntax error is detected
   * @throws NullPointerException when any argument is {@code null}
   */
  public URI asURI(Path scss) throws IOException {
    return URI.create(asURIString(scss));
  }

  /**
   * Compiles the given scss file into a valid URI string. The URI includes the CSS
   * as base64 encoded text.
   *
   * @param scss a SCSS file to compile, cannot be {@code null}
   * @return a URI string containing the base64 encoded CSS, never {@code null}
   * @throws IOException when an IO error occurred
   * @throws SCSSProcessingException when a compilation or syntax error is detected
   * @throws NullPointerException when any argument is {@code null}
   */
  public String asURIString(Path scss) throws IOException {
    StringBuilder builder = new StringBuilder();
    MessageConsumer messageConsumer = new MessageConsumer();

    builder.append("data:text/css;charset=UTF-8;base64,");

    try(
      InputStream stdout = asStream(scss, messageConsumer);
      OutputStream outputStream = new ASCIIStringBuilderOutputStream(builder);
      OutputStream wrap = Base64.getEncoder().wrap(outputStream)
    ) {
      stdout.transferTo(wrap);

      messageConsumer.callHandlers();
    }

    return builder.toString();
  }

  /**
   * Compiles the given scss file into and returns the result as a buffered
   * stream, while sending any lines output on standard error to the {@code errorLines}
   * consumer.
   * <p>
   * The {@code errorLines} consumer is called with each line that appears on the
   * standard output. The last line send will be {@code null} to indicate the end
   * of the stream was reached.
   * <p>
   * The stream should be closed after use.
   *
   * @param scss a SCSS file to compile, cannot be {@code null}
   * @param errorLines a {@link Consumer} called for each line output on standard error, cannot be {@code null}
   * @return a buffered {@link InputStream}, never {@code null}
   * @throws IOException when an IO error occurred
   * @throws SCSSProcessingException when a compilation or syntax error is detected
   * @throws NullPointerException when any argument is {@code null}
   */
  public InputStream asStream(Path scss, Consumer<String> errorLines) throws IOException {
    Objects.requireNonNull(scss, "scss");
    Objects.requireNonNull(errorLines, "errorLines");

    if(TEMP_DIRECTORY == null) {
      throw new IllegalStateException("Dart SCSS compiler could not be initialized, cannot compile SCSS file: " + scss);
    }

    Process process = createProcess(root, scss);

    EXECUTOR.execute(() -> {
      try(BufferedReader reader = process.errorReader(StandardCharsets.UTF_8)) {
        for(;;) {
          String line = reader.readLine();

          errorLines.accept(line);  // purposely sending null to signal to consumer that EOF was reached

          if(line == null) {
            break;
          }
        }
      }
      catch(IOException e) {
        throw new IllegalStateException("IOException while compiling: " + scss, e);
      }
    });

    return new BufferedInputStream(process.getInputStream());
  }

  private static Process createProcess(Path root, Path scss) throws IOException {
    List<String> command = new ArrayList<>();

    switch(OS) {
      case WINDOWS -> command.addAll(List.of("cmd.exe", "/c", TEMP_DIRECTORY.resolve("dart-sass/sass.bat").toString()));
      default -> command.addAll(List.of("/bin/bash", TEMP_DIRECTORY.resolve("dart-sass/sass").toString()));
    }

    command.addAll(List.of("--no-source-map", "--load-path", root.toString(), "--style", "compressed", scss.toString()));

    ProcessBuilder processBuilder = new ProcessBuilder(command);

    return processBuilder.start();
  }

  private static OperatingSystem getOS() {
    String os = System.getProperty("os.name").toLowerCase();

    if(os.contains("win")) {
      return OperatingSystem.WINDOWS;
    }

    if(os.contains("mac")) {
      return OperatingSystem.MAC;
    }

    if(os.contains("nix") || os.contains("nux")) {
      return OperatingSystem.LINUX;
    }

    throw new IllegalStateException("Unsupported OS type: " + os);
  }

  private static void deleteDirectoryRecursively(Path directory) throws IOException {
    try(Stream<Path> stream = Files.walk(directory)) {
      for(Iterator<Path> iterator = stream.sorted(Comparator.reverseOrder()).iterator(); iterator.hasNext();) {
        Files.delete(iterator.next());
      }
    }
  }

  class MessageConsumer implements Consumer<String> {
    private static final Pattern PATTERN = Pattern.compile("(DEPRECATION WARNING|WARNING|ERROR).*:.*", Pattern.CASE_INSENSITIVE);

    private final StringBuilder builder = new StringBuilder();
    private final Semaphore busy = new Semaphore(0);
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final List<String> deprecations = new ArrayList<>();

    private String type;

    @Override
    public void accept(String text) {
      synchronized(builder) {
        if(text == null) {
          finish();
          busy.release();
        }
        else {
          Matcher matcher = PATTERN.matcher(text);

          if(matcher.matches()) {
            finish();

            type = matcher.group(1);
          }

          builder.append(text).append("\n");
        }
      }
    }

    public void callHandlers() {
      busy.acquireUninterruptibly();

      synchronized(builder) {
        deprecationsHandler.accept(deprecations);
        warningsHandler.accept(warnings);
        errorsHandler.accept(errors);
      }
    }

    private void finish() {
      if(!builder.isEmpty()) {
        if("ERROR".equalsIgnoreCase(type)) {
          errors.add(builder.toString());
        }
        else if("WARNING".equalsIgnoreCase(type)) {
          warnings.add(builder.toString());
        }
        else {
          deprecations.add(builder.toString());
        }

        builder.setLength(0);
      }
    }
  }
}
