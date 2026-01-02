package me.hanju.parsekit.common;

import java.io.IOException;
import java.util.Set;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class FileTypeDetector {

  private static final Tika TIKA = new Tika();

  // 텍스트 문서 (ODT 변환 가능)
  private static final Set<String> TEXT_DOCUMENT_TYPES = Set.of(
      // Microsoft Word
      "application/msword", // .doc
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // .docx
      "application/vnd.ms-word.template.macroEnabled.12", // .dotm
      "application/vnd.openxmlformats-officedocument.wordprocessingml.template", // .dotx
      // OpenDocument Text
      "application/vnd.oasis.opendocument.text", // .odt
      "application/vnd.oasis.opendocument.text-template", // .ott
      "application/vnd.oasis.opendocument.text-flat-xml", // .fodt
      // Hancom (한글)
      "application/x-hwp", // .hwp
      "application/hwp", // .hwp (alternative)
      "application/vnd.hancom.hwp", // .hwp (alternative)
      "application/vnd.hancom.hwpx", // .hwpx
      "application/hwp+zip", // .hwpx (alternative)
      "application/x-tika-msoffice", // .hwp (Tika fallback for legacy HWP)
      // Text formats
      "text/plain", // .txt
      "text/rtf", // .rtf
      "application/rtf", // .rtf (alternative)
      // HTML
      "text/html", // .html, .htm
      "application/xhtml+xml", // .xhtml
      // WordPerfect
      "application/vnd.wordperfect", // .wpd
      // Other text formats
      "application/x-abiword", // .abw
      "text/xml", // .xml (Word 2003 XML)
      "application/xml" // .xml (alternative)
  );

  // 스프레드시트 문서
  private static final Set<String> SPREADSHEET_TYPES = Set.of(
      "application/vnd.ms-excel", // .xls
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", // .xlsx
      "application/vnd.ms-excel.template.macroEnabled.12", // .xltm
      "application/vnd.openxmlformats-officedocument.spreadsheetml.template", // .xltx
      "application/vnd.oasis.opendocument.spreadsheet", // .ods
      "application/vnd.oasis.opendocument.spreadsheet-template", // .ots
      "application/vnd.oasis.opendocument.spreadsheet-flat-xml", // .fods
      "text/csv" // .csv
  );

  // 프레젠테이션 문서
  private static final Set<String> PRESENTATION_TYPES = Set.of(
      "application/vnd.ms-powerpoint", // .ppt
      "application/vnd.openxmlformats-officedocument.presentationml.presentation", // .pptx
      "application/vnd.ms-powerpoint.template.macroEnabled.12", // .potm
      "application/vnd.openxmlformats-officedocument.presentationml.template", // .potx
      "application/vnd.oasis.opendocument.presentation", // .odp
      "application/vnd.oasis.opendocument.presentation-template", // .otp
      "application/vnd.oasis.opendocument.presentation-flat-xml" // .fodp
  );

  private static final Set<String> PDF_TYPES = Set.of(
      "application/pdf");

  private static final Set<String> IMAGE_TYPES = Set.of(
      "image/png",
      "image/jpeg",
      "image/gif",
      "image/webp",
      "image/bmp",
      "image/tiff");

  // Docling이 직접 지원하는 문서 형식
  private static final Set<String> DOCLING_SUPPORTED_TYPES = Set.of(
      "application/pdf",
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // .docx
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", // .xlsx
      "application/vnd.openxmlformats-officedocument.presentationml.presentation", // .pptx
      "application/vnd.hancom.hwp", // .hwp
      "application/x-hwp", // .hwp (alternative)
      "application/hwp" // .hwp (alternative)
  );

  // 플레인 텍스트 (파싱 시 변환 없이 그대로 반환)
  private static final Set<String> PLAIN_TEXT_TYPES = Set.of(
      "text/plain");

  // Markdown (HTML로 변환 후 LibreOffice로 처리)
  private static final Set<String> MARKDOWN_TYPES = Set.of(
      "text/markdown",
      "text/x-markdown");

  public String detectMimeType(MultipartFile file) throws IOException {
    return TIKA.detect(file.getBytes(), file.getOriginalFilename());
  }

  public String detectMimeType(byte[] content, String filename) {
    return TIKA.detect(content, filename);
  }

  /**
   * ODT 변환 가능한 문서인지 확인 (텍스트 문서만)
   */
  public boolean isSupportedForOdt(MultipartFile file) throws IOException {
    String mimeType = detectMimeType(file);
    return isSupportedForOdt(mimeType);
  }

  public boolean isSupportedForOdt(String mimeType) {
    return TEXT_DOCUMENT_TYPES.contains(mimeType)
        || MARKDOWN_TYPES.contains(mimeType);
  }

  /**
   * PDF 변환 가능한 문서인지 확인 (모든 문서 타입 + PDF)
   */
  public boolean isSupportedForPdf(MultipartFile file) throws IOException {
    String mimeType = detectMimeType(file);
    return isSupportedForPdf(mimeType);
  }

  public boolean isSupportedForPdf(String mimeType) {
    return TEXT_DOCUMENT_TYPES.contains(mimeType)
        || SPREADSHEET_TYPES.contains(mimeType)
        || PRESENTATION_TYPES.contains(mimeType)
        || PDF_TYPES.contains(mimeType)
        || MARKDOWN_TYPES.contains(mimeType);
  }

  /**
   * 이미지 변환 가능한 문서인지 확인 (PDF 변환 가능한 문서)
   */
  public boolean isSupportedForImages(MultipartFile file) throws IOException {
    return isSupportedForPdf(file);
  }

  public boolean isSupportedForImages(String mimeType) {
    return isSupportedForPdf(mimeType);
  }

  public boolean isTextDocument(String mimeType) {
    return TEXT_DOCUMENT_TYPES.contains(mimeType);
  }

  public boolean isSpreadsheet(String mimeType) {
    return SPREADSHEET_TYPES.contains(mimeType);
  }

  public boolean isPresentation(String mimeType) {
    return PRESENTATION_TYPES.contains(mimeType);
  }

  public boolean isPdf(MultipartFile file) throws IOException {
    String mimeType = detectMimeType(file);
    return PDF_TYPES.contains(mimeType);
  }

  public boolean isPdf(byte[] content, String filename) {
    String mimeType = detectMimeType(content, filename);
    return PDF_TYPES.contains(mimeType);
  }

  public boolean isPdf(String mimeType) {
    return PDF_TYPES.contains(mimeType);
  }

  public boolean isImage(byte[] content) {
    String mimeType = detectMimeType(content, null);
    return IMAGE_TYPES.contains(mimeType);
  }

  public boolean isImage(String mimeType) {
    return IMAGE_TYPES.contains(mimeType);
  }

  public String detectImageMimeType(byte[] content) {
    String mimeType = detectMimeType(content, null);
    return IMAGE_TYPES.contains(mimeType) ? mimeType : "image/png";
  }

  public boolean isDoclingSupported(String mimeType) {
    return DOCLING_SUPPORTED_TYPES.contains(mimeType);
  }

  public boolean isDoclingSupported(byte[] content, String filename) {
    String mimeType = detectMimeType(content, filename);
    return DOCLING_SUPPORTED_TYPES.contains(mimeType);
  }

  /**
   * 플레인 텍스트인지 확인 (파싱 시 변환 없이 그대로 반환)
   */
  public boolean isPlainText(String mimeType) {
    return PLAIN_TEXT_TYPES.contains(mimeType);
  }

  public boolean isPlainText(byte[] content, String filename) {
    String mimeType = detectMimeType(content, filename);
    return PLAIN_TEXT_TYPES.contains(mimeType);
  }

  /**
   * Markdown인지 확인
   */
  public boolean isMarkdown(String mimeType) {
    return MARKDOWN_TYPES.contains(mimeType);
  }

  public boolean isMarkdown(byte[] content, String filename) {
    String mimeType = detectMimeType(content, filename);
    return MARKDOWN_TYPES.contains(mimeType);
  }

  /**
   * 플레인 텍스트 또는 Markdown인지 확인 (파싱 시 그대로 반환되는 타입)
   */
  public boolean isText(String mimeType) {
    return PLAIN_TEXT_TYPES.contains(mimeType) || MARKDOWN_TYPES.contains(mimeType);
  }

  public boolean isText(byte[] content, String filename) {
    String mimeType = detectMimeType(content, filename);
    return isText(mimeType);
  }
}
