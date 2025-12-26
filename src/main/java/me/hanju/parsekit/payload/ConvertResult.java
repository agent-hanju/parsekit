package me.hanju.parsekit.payload;

/**
 * LibreOffice 변환 결과
 */
public record ConvertResult(
    String filename,
    byte[] content,
    int size,
    boolean converted) {
}
