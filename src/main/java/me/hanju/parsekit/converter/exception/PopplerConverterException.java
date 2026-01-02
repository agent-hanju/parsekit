package me.hanju.parsekit.converter.exception;

import me.hanju.parsekit.common.ParseKitException;

public class PopplerConverterException extends ParseKitException {
  public PopplerConverterException(Throwable cause) {
    super(cause);
  }

  public PopplerConverterException(String message, Throwable cause) {
    super(message, cause);
  }
}
