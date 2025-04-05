package org.int4.scss.compiler;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SCSSCompilerTest {
  private final Path root = Path.of("styles");

  @Test
  void shouldCompileSCSSWithDependency() throws IOException {
    String result = SCSSCompiler.compile(root, root.resolve("org/int4/scss/styles.scss"));

    assertThat(result).isEqualTo(".container{color:red}.container .header{background-color:red}\n");
  }

  @Nested
  class GivenACompiler {
    List<String> errors = List.of();
    List<String> warnings = List.of();
    List<String> deprecations = List.of();
    SCSSCompiler compiler = SCSSCompiler.of(root, list -> errors = list, list -> warnings = list, list -> deprecations = list);

    @Test
    void shouldCompileToString() throws IOException {
      String result = compiler.asString(root.resolve("org/int4/scss/styles.scss"));

      assertThat(result).isEqualTo(".container{color:red}.container .header{background-color:red}\n");
      assertThat(errors).isEmpty();
      assertThat(warnings).isEmpty();
      assertThat(deprecations).isEmpty();
    }

    @Test
    void shouldCompileToURIString() throws IOException {
      String result = compiler.asURIString(root.resolve("org/int4/scss/styles.scss"));

      assertThat(result).isEqualTo("data:text/css;charset=UTF-8;base64,LmNvbnRhaW5lcntjb2xvcjpyZWR9LmNvbnRhaW5lciAuaGVhZGVye2JhY2tncm91bmQtY29sb3I6cmVkfQo=");
      assertThat(errors).isEmpty();
      assertThat(warnings).isEmpty();
      assertThat(deprecations).isEmpty();
    }

    @Test
    void shouldCompileToURI() throws IOException {
      URI result = compiler.asURI(root.resolve("org/int4/scss/styles.scss"));

      assertThat(result).isEqualTo(URI.create("data:text/css;charset=UTF-8;base64,LmNvbnRhaW5lcntjb2xvcjpyZWR9LmNvbnRhaW5lciAuaGVhZGVye2JhY2tncm91bmQtY29sb3I6cmVkfQo="));
      assertThat(errors).isEmpty();
      assertThat(warnings).isEmpty();
      assertThat(deprecations).isEmpty();
    }

    @Test
    void shouldProvideErrors() throws IOException {
      String result = compiler.asString(root.resolve("org/int4/scss/missing.scss"));

      assertThat(result).isEmpty();
      assertThat(errors).satisfiesExactlyInAnyOrder(
        str -> assertThat(str).matches(toPattern(
          """
          Error reading styles/org/int4/scss/missing.scss: Cannot open file.
          """
        ))
      );
      assertThat(warnings).isEmpty();
      assertThat(deprecations).isEmpty();
    }

    @Test
    void shouldProvideWarnings() throws IOException {
      String result = compiler.asString(root.resolve("org/int4/scss/warn.scss"));

      assertThat(result).isEqualTo(".tilt{-wekbit-transform:rotate(15deg);-ms-transform:rotate(15deg);transform:rotate(15deg)}\n");
      assertThat(errors).isEmpty();
      assertThat(warnings).satisfiesExactlyInAnyOrder(
        str -> assertThat(str).matches(toPattern(
          """
          WARNING: Unknown prefix wekbit.
              styles/org/int4/scss/warn.scss 8:7   prefix()
              styles/org/int4/scss/warn.scss 18:3  root stylesheet

          """
        ))
      );
      assertThat(deprecations).isEmpty();
    }

    @Test
    void shouldProvidDeprecations() throws IOException {
      String result = compiler.asString(root.resolve("org/int4/scss/deprecation.scss"));

      assertThat(result).isEqualTo(".tilt{-wekbit-transform:rotate(15deg);-ms-transform:rotate(15deg);transform:rotate(15deg)}\n");
      assertThat(errors).isEmpty();
      assertThat(warnings).satisfiesExactlyInAnyOrder(
        str -> assertThat(str).matches(toPattern(
          """
          WARNING: Unknown prefix wekbit.
              styles/org/int4/scss/deprecation.scss 6:7   prefix()
              styles/org/int4/scss/deprecation.scss 16:3  root stylesheet

          """
        ))
      );
      assertThat(deprecations).satisfiesExactlyInAnyOrder(
        str -> assertThat(str).matches(toPattern(
          """
          DEPRECATION WARNING [global-builtin]: Global built-in functions are deprecated and will be removed in Dart Sass 3.0.0.
          Use list.index instead.

          More info and automated migrator: https://sass-lang.com/d/import

            ╷
          5 │     @if not index($known-prefixes, $prefix) {
            │             ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
            ╵
              styles/org/int4/scss/deprecation.scss 5:13  prefix()
              styles/org/int4/scss/deprecation.scss 16:3  root stylesheet

          """
        ))
      );
    }
  }

  @Test
  void shouldReturnReadableErrorWhenMissing() {
    assertThatThrownBy(() -> SCSSCompiler.compile(root, root.resolve("org/int4/scss/missing.scss")))
      .isInstanceOf(SCSSProcessingException.class)
      .message()
      .matches(toPattern(
        """
        Error reading styles/org/int4/scss/missing.scss: Cannot open file.
        """
      ));
  }

  @Test
  void shouldReturnReadableErrorWhenBad() {
    assertThatThrownBy(() -> SCSSCompiler.compile(root, root.resolve("org/int4/scss/bad.scss")))
      .isInstanceOf(SCSSProcessingException.class)
      .message()
      .matches(toPattern(
        """
        Error: Undefined variable.
          ╷
        4 │   color: colors.$missing-color;
          │          ^^^^^^^^^^^^^^^^^^^^^
          ╵
          styles/org/int4/scss/bad.scss 4:10  root stylesheet
        """
      ));
  }

  Pattern toPattern(String pattern) {
    return Pattern.compile(
      pattern.replace("$", "\\$")
        .replace("^", "\\^")
        .replace("(", "\\(")
        .replace(")", "\\)")
        .replace("[", "\\[")
        .replace("]", "\\]")
        .replace("{", "\\{")
        .replace("}", "\\}")
        .replace(".", "\\.")
        .replace("/", "[\\\\/]")
    );
  }
}
