package me.hanju.parsekit.payload;

import java.util.List;

/**
 * 문서 → 이미지 변환 결과
 */
public record ImageConvertResult(
    String format,
    int totalPages,
    List<ImagePage> pages) {
}
