package me.hanju.parsekit.payload;

/**
 * 표준 API 응답 래퍼
 */
public record ApiResponse<T>(
    int code,
    String message,
    T data) {

  public boolean isSuccess() {
    return code == 0;
  }
}
