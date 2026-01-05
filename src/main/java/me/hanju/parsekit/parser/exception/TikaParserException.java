package me.hanju.parsekit.parser.exception;

import me.hanju.parsekit.common.exception.ParseKitException;

public class TikaParserException extends ParseKitException {
  public TikaParserException(Throwable cause) {
    super(cause);
  }

  public TikaParserException(String message, Throwable cause) {
    super(message, cause);
  }
}
