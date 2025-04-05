# Java SCSS Compiler

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.int4.scss/scss-compiler/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.int4.scss/scss-compiler)
[![Build Status](https://github.com/int4-org/SCSS/actions/workflows/maven.yml/badge.svg?branch=master)](https://github.com/int4-org/SCSS/actions)
[![Coverage](https://codecov.io/gh/int4-org/SCSS/branch/master/graph/badge.svg?token=QCNNRFYF98)](https://codecov.io/gh/int4-org/SCSS)
[![License](https://img.shields.io/badge/License-BSD_2--Clause-orange.svg)](https://opensource.org/licenses/BSD-2-Clause)
[![javadoc](https://javadoc.io/badge2/org.int4.scss/parent/javadoc.svg)](https://javadoc.io/doc/org.int4.scss/parent)

A compiler for SCSS files that can be easily used from Java code. It uses the
Dart SCSS compiler internally.

## Introduction

The Java SCSS Compiler is a lightweight library that allows you to compile SCSS files into CSS directly from Java. It leverages the Dart SCSS compiler to ensure compatibility with modern SCSS features. This library is particularly useful for JavaFX applications or any Java-based project that requires dynamic SCSS compilation.

### Key Features

- Compile SCSS files to CSS strings or Base64-encoded URIs.
- Cross-platform support (Windows, macOS, Linux).
- Customizable error, warning, and deprecation handlers.

## Usage

### Basic Usage

To compile an SCSS file into a CSS string:
```java
import org.int4.scss.compiler.SCSSCompiler;

import java.nio.file.Path;

public class Example {
  public static void main(String[] args) throws Exception {
    Path root = Path.of("styles");
    SCSSCompiler compiler = SCSSCompiler.of(root);
    String css = compiler.asString(Path.of("styles/dark-theme.scss"));
    System.out.println(css);
  }
}
```

### JavaFX Integration

You can use the SCSS compiler to dynamically load stylesheets in a JavaFX application:

```java
import org.int4.scss.compiler.SCSSCompiler;

import java.nio.file.Path;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class JavaFXExample extends Application {
  @Override
  public void start(Stage stage) throws Exception {
    SCSSCompiler compiler = SCSSCompiler.of(Path.of("styles"));

    StackPane root = new StackPane();
    Scene scene = new Scene(root, 400, 300);

    // Add compiled SCSS as a stylesheet
    scene.getStylesheets().add(compiler.asURIString(Path.of("styles/dark-theme.scss")));

    stage.setScene(scene);
    stage.setTitle("JavaFX SCSS Example");
    stage.show();
  }

  public static void main(String[] args) {
    launch();
  }
}
```
### Error Handling

By default, when the compiler encounters an error during the compilation process, it wraps the error in a `SCSSProcessingException`. Warnings are logged at the warning level, and deprecations are logged at the info level.

#### Default Behavior
- Errors are wrapped in a `SCSSProcessingException`.
- Warnings are logged at the warning level.
- Deprecations are logged at the info level.

#### Custom Handlers
You can override the default behavior by providing custom error, warning, or deprecation handlers. These handlers receive a `List<String>` containing all errors, warnings, or deprecations that occurred, respectively. Note that if you override the default error handler, the compiler will not throw a `SCSSProcessingException` unless your custom handler explicitly does so. If `null` is passed for any of the handlers, the default behavior for that handler will be used:

```java
import org.int4.scss.compiler.SCSSCompiler;
import org.int4.scss.compiler.SCSSProcessingException;

import java.nio.file.Path;
import java.util.List;

public class CustomErrorHandlingExample {
    public static void main(String[] args) {
        SCSSCompiler compiler = SCSSCompiler.of(
            Path.of("styles"), // Root for use/import statements
            errors -> {
                if (!errors.isEmpty()) {
                    throw new SCSSProcessingException("Custom error handler detected errors: " + errors);
                }
            },
            warnings -> warnings.forEach(warning -> System.out.println("Warning: " + warning)),
            null // Using default behavior for deprecations
        );

        try {
            String css = compiler.asString(Path.of("styles/dark-theme.scss"));
            System.out.println(css);
        }
        catch (SCSSProcessingException e) {
            System.err.println("Compilation failed: " + e.getMessage());
        }
    }
}
```

This allows you to log or handle issues in a way that suits your application's needs while maintaining control over how errors are propagated.

# BSD License

Copyright (c) 2025, John Hendrikx
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

# Dependencies and Acknowledgments

### Dart Sass

License: MIT License
https://github.com/sass/dart-sass
