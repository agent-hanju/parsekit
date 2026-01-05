package me.hanju.parsekit.common.exception;

public class ParseKitException extends RuntimeException {
  public ParseKitException(Throwable cause) {
    super(cause);
  }

  public ParseKitException(String message, Throwable cause) {
    super(message, cause);
  }
}
