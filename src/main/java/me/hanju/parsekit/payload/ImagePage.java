package me.hanju.parsekit.payload;

/**
 * 이미지 변환 결과의 페이지 정보
 */
public record ImagePage(
    int page,
    byte[] content,
    int size,
    int totalPages) {
}
