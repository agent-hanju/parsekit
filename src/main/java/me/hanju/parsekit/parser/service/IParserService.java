package me.hanju.parsekit.parser.service;

import me.hanju.parsekit.parser.dto.ParseResult;

/**
 * 파서 서비스 인터페이스.
 * 설정에 따라 Hybrid, Docling, VLM, Tika 중 하나의 구현체가 Bean으로 등록된다.
 */
public interface IParserService {

  /**
   * 파일을 파싱하여 마크다운으로 변환한다.
   *
   * @param content  파일 내용
   * @param filename 원본 파일명
   * @param dpi      이미지 변환 시 해상도 (필요한 경우)
   * @return 파싱 결과
   */
  ParseResult parse(byte[] content, String filename, int dpi);

  /**
   * 기본 DPI(150)로 파싱한다.
   */
  default ParseResult parse(byte[] content, String filename) {
    return parse(content, filename, 150);
  }
}
