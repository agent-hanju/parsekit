package dev.hanju.parsekit.parser.exception;

import dev.hanju.parsekit.common.exception.ParseKitException;

public class DoclingClientException extends ParseKitException {

  public DoclingClientException(Throwable cause) {
    super(cause);
  }

  public DoclingClientException(String message, Throwable cause) {
    super(message, cause);
  }
}
