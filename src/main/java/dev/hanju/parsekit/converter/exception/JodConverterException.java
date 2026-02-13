package dev.hanju.parsekit.converter.exception;

import dev.hanju.parsekit.common.exception.ParseKitException;

public class JodConverterException extends ParseKitException {
  public JodConverterException(Throwable cause) {
    super(cause);
  }

  public JodConverterException(String message, Throwable cause) {
    super(message, cause);
  }
}
