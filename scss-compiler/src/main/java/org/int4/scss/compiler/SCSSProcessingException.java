package org.int4.scss.compiler;

/**
 * Runtime exception thrown by the default error handler when an error occurred
 * during SCSS processing.
 */
public class SCSSProcessingException extends RuntimeException {

  /**
   * Constructs a new instance.
   *
   * @param message a message, can be {@code null}
   */
  public SCSSProcessingException(String message) {
    super(message);
  }
}
