package me.hanju.parsekit.exception;

/**
 * 클라이언트 측 에러 (네트워크, 타임아웃 등)
 */
public final class ParseKitClientException extends RuntimeException {

  public ParseKitClientException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
