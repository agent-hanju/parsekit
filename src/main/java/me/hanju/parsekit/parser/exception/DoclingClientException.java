package me.hanju.parsekit.parser.exception;

import me.hanju.parsekit.common.exception.ParseKitException;

public class DoclingClientException extends ParseKitException {

  public DoclingClientException(Throwable cause) {
    super(cause);
  }

  public DoclingClientException(String message, Throwable cause) {
    super(message, cause);
  }
}
