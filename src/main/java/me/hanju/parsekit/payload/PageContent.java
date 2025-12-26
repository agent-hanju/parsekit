package me.hanju.parsekit.payload;

/**
 * 파싱된 페이지 내용
 */
public record PageContent(
    int page,
    String markdown) {
}
