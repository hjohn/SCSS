package org.int4.scss.compiler;

import java.io.OutputStream;

/*
 * Only use for ASCII characters (like base64 produces)
 */
class ASCIIStringBuilderOutputStream extends OutputStream {
  private final StringBuilder builder;

  public ASCIIStringBuilderOutputStream(StringBuilder builder) {
    this.builder = builder;
  }

  @Override
  public void write(int b) {
    builder.append((char)b);  // base64 chars are safe ASCII
  }
}
