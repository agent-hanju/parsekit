package me.hanju.parsekit.exception;

/**
 * ParseKit API 에러 (서버가 에러 응답 반환)
 */
public final class ParseKitException extends RuntimeException {

  private final int code;

  public ParseKitException(final int code, final String message) {
    super(message);
    this.code = code;
  }

  public int getCode() {
    return code;
  }
}
