package me.hanju.parsekit.converter.exception;

import me.hanju.parsekit.common.ParseKitException;

public class JodConverterException extends ParseKitException {
  public JodConverterException(Throwable cause) {
    super(cause);
  }

  public JodConverterException(String message, Throwable cause) {
    super(message, cause);
  }
}
