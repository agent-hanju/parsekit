package me.hanju.parsekit;

import me.hanju.parsekit.payload.ParseResult;

/**
 * 문서 파서 공통 인터페이스
 */
public interface IParser {

  /**
   * 문서 파싱
   *
   * @param content  문서 바이트 배열
   * @param filename 파일명
   * @return 파싱 결과
   */
  ParseResult parse(byte[] content, String filename);

  /**
   * 이미지 OCR
   *
   * @param imageBytes 이미지 바이트 배열
   * @return 추출된 마크다운 텍스트
   */
  String ocr(byte[] imageBytes);

  /**
   * 서비스 가용 여부 확인
   */
  boolean isAvailable();
}
