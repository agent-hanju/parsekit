package me.hanju.parsekit.parser.exception;

import me.hanju.parsekit.common.ParseKitException;

public class VlmClientException extends ParseKitException {

  public VlmClientException(Throwable cause) {
    super(cause);
  }

  public VlmClientException(String message, Throwable cause) {
    super(message, cause);
  }
}
