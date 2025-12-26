package me.hanju.parsekit.docling;

/**
 * Docling 이미지 내보내기 모드
 *
 * @see <a href="https://github.com/docling-project/docling-serve">Docling Serve API</a>
 */
public enum ImageExportMode {

  /**
   * 이미지를 base64로 인코딩하여 마크다운에 인라인으로 포함
   * 예: ![Image](data:image/png;base64,...)
   */
  EMBEDDED("embedded"),

  /**
   * 이미지 대신 플레이스홀더 텍스트만 표시
   * 예: Object 1
   */
  PLACEHOLDER("placeholder");

  private final String value;

  ImageExportMode(final String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
