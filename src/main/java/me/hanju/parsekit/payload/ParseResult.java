package me.hanju.parsekit.payload;

import java.util.List;
import java.util.Map;

/**
 * 파싱 결과 (페이지 목록 + 메타데이터)
 */
public record ParseResult(
    List<PageContent> pages,
    Map<String, String> metadata) {

  /**
   * 전체 페이지를 단일 마크다운 텍스트로 반환
   */
  public String asMarkdown() {
    StringBuilder sb = new StringBuilder();
    for (PageContent page : pages) {
      if (!sb.isEmpty()) {
        sb.append("\n\n---\n\n");
      }
      sb.append(page.markdown());
    }
    return sb.toString();
  }
}
