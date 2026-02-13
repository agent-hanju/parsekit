package dev.hanju.parsekit.converter.exception;

import dev.hanju.parsekit.common.exception.ParseKitException;

public class PopplerConverterException extends ParseKitException {
  public PopplerConverterException(Throwable cause) {
    super(cause);
  }

  public PopplerConverterException(String message, Throwable cause) {
    super(message, cause);
  }
}
