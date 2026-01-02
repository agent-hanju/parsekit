package me.hanju.parsekit.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class FileTypeDetectorTest {

  private FileTypeDetector detector;

  // 1x1 PNG 이미지 바이트
  private static final byte[] PNG_BYTES = new byte[] {
      (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
      0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
      0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
      0x08, 0x02, 0x00, 0x00, 0x00, (byte) 0x90, 0x77, 0x53,
      (byte) 0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41,
      0x54, 0x08, (byte) 0xD7, 0x63, (byte) 0xF8, (byte) 0xFF, (byte) 0xFF,
      0x3F, 0x00, 0x05, (byte) 0xFE, 0x02, (byte) 0xFE, (byte) 0xDC,
      (byte) 0xCC, 0x59, (byte) 0xE7, 0x00, 0x00, 0x00, 0x00,
      0x49, 0x45, 0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82
  };

  // JPEG 시그니처
  private static final byte[] JPEG_BYTES = new byte[] {
      (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10,
      0x4A, 0x46, 0x49, 0x46, 0x00, 0x01
  };

  // PDF 시그니처
  private static final byte[] PDF_BYTES = "%PDF-1.4\n".getBytes();

  @BeforeEach
  void setUp() {
    detector = new FileTypeDetector();
  }

  @Nested
  @DisplayName("detectMimeType()")
  class DetectMimeType {

    @Test
    @DisplayName("PNG 이미지의 MIME 타입을 감지한다")
    void shouldDetectPngMimeType() {
      String mimeType = detector.detectMimeType(PNG_BYTES, "test.png");
      assertThat(mimeType).isEqualTo("image/png");
    }

    @Test
    @DisplayName("JPEG 이미지의 MIME 타입을 감지한다")
    void shouldDetectJpegMimeType() {
      String mimeType = detector.detectMimeType(JPEG_BYTES, "test.jpg");
      assertThat(mimeType).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("PDF 문서의 MIME 타입을 감지한다")
    void shouldDetectPdfMimeType() {
      String mimeType = detector.detectMimeType(PDF_BYTES, "test.pdf");
      assertThat(mimeType).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("텍스트 파일의 MIME 타입을 감지한다")
    void shouldDetectTextMimeType() {
      byte[] textBytes = "Hello, World!".getBytes();
      String mimeType = detector.detectMimeType(textBytes, "test.txt");
      assertThat(mimeType).isEqualTo("text/plain");
    }

    @Test
    @DisplayName("MultipartFile에서 MIME 타입을 감지한다")
    void shouldDetectMimeTypeFromMultipartFile() throws IOException {
      MockMultipartFile file = new MockMultipartFile(
          "file", "test.png", "image/png", PNG_BYTES);

      String mimeType = detector.detectMimeType(file);
      assertThat(mimeType).isEqualTo("image/png");
    }
  }

  @Nested
  @DisplayName("isPdf()")
  class IsPdf {

    @Test
    @DisplayName("PDF 파일을 인식한다")
    void shouldRecognizePdf() {
      assertThat(detector.isPdf(PDF_BYTES, "test.pdf")).isTrue();
    }

    @Test
    @DisplayName("PDF가 아닌 파일을 구분한다")
    void shouldNotRecognizeNonPdf() {
      assertThat(detector.isPdf(PNG_BYTES, "test.png")).isFalse();
    }

    @Test
    @DisplayName("MIME 타입 문자열로 PDF를 인식한다")
    void shouldRecognizePdfByMimeType() {
      assertThat(detector.isPdf("application/pdf")).isTrue();
      assertThat(detector.isPdf("image/png")).isFalse();
    }
  }

  @Nested
  @DisplayName("isImage()")
  class IsImage {

    @Test
    @DisplayName("PNG 이미지를 인식한다")
    void shouldRecognizePng() {
      assertThat(detector.isImage(PNG_BYTES)).isTrue();
    }

    @Test
    @DisplayName("JPEG 이미지를 인식한다")
    void shouldRecognizeJpeg() {
      assertThat(detector.isImage(JPEG_BYTES)).isTrue();
    }

    @Test
    @DisplayName("이미지가 아닌 파일을 구분한다")
    void shouldNotRecognizeNonImage() {
      assertThat(detector.isImage(PDF_BYTES)).isFalse();
    }

    @Test
    @DisplayName("MIME 타입 문자열로 이미지를 인식한다")
    void shouldRecognizeImageByMimeType() {
      assertThat(detector.isImage("image/png")).isTrue();
      assertThat(detector.isImage("image/jpeg")).isTrue();
      assertThat(detector.isImage("image/gif")).isTrue();
      assertThat(detector.isImage("image/webp")).isTrue();
      assertThat(detector.isImage("application/pdf")).isFalse();
    }
  }

  @Nested
  @DisplayName("isText()")
  class IsText {

    @Test
    @DisplayName("텍스트 파일을 인식한다")
    void shouldRecognizeText() {
      byte[] textBytes = "Hello, World!".getBytes();
      assertThat(detector.isText(textBytes, "test.txt")).isTrue();
    }

    @Test
    @DisplayName("마크다운 파일을 인식한다")
    void shouldRecognizeMarkdown() {
      assertThat(detector.isText("text/markdown")).isTrue();
      assertThat(detector.isText("text/x-markdown")).isTrue();
    }

    @Test
    @DisplayName("MIME 타입 문자열로 텍스트를 인식한다")
    void shouldRecognizeTextByMimeType() {
      assertThat(detector.isText("text/plain")).isTrue();
      assertThat(detector.isText("application/pdf")).isFalse();
    }
  }

  @Nested
  @DisplayName("isSupportedForOdt()")
  class IsSupportedForOdt {

    @Test
    @DisplayName("텍스트 문서를 ODT 변환 지원 형식으로 인식한다")
    void shouldRecognizeTextDocumentsAsSupported() {
      assertThat(detector.isSupportedForOdt("application/msword")).isTrue();
      assertThat(detector.isSupportedForOdt(
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document")).isTrue();
      assertThat(detector.isSupportedForOdt("application/vnd.oasis.opendocument.text")).isTrue();
      assertThat(detector.isSupportedForOdt("text/html")).isTrue();
    }

    @Test
    @DisplayName("한글 문서를 ODT 변환 지원 형식으로 인식한다")
    void shouldRecognizeHwpAsSupported() {
      assertThat(detector.isSupportedForOdt("application/x-hwp")).isTrue();
      assertThat(detector.isSupportedForOdt("application/vnd.hancom.hwp")).isTrue();
      assertThat(detector.isSupportedForOdt("application/vnd.hancom.hwpx")).isTrue();
    }

    @Test
    @DisplayName("스프레드시트는 ODT 변환 미지원이다")
    void shouldNotRecognizeSpreadsheetAsSupported() {
      assertThat(detector.isSupportedForOdt("application/vnd.ms-excel")).isFalse();
      assertThat(detector.isSupportedForOdt(
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).isFalse();
    }

    @Test
    @DisplayName("프레젠테이션은 ODT 변환 미지원이다")
    void shouldNotRecognizePresentationAsSupported() {
      assertThat(detector.isSupportedForOdt("application/vnd.ms-powerpoint")).isFalse();
      assertThat(detector.isSupportedForOdt(
          "application/vnd.openxmlformats-officedocument.presentationml.presentation")).isFalse();
    }

    @Test
    @DisplayName("이미지는 ODT 변환 미지원이다")
    void shouldNotRecognizeImageAsSupported() {
      assertThat(detector.isSupportedForOdt("image/png")).isFalse();
    }
  }

  @Nested
  @DisplayName("isSupportedForPdf()")
  class IsSupportedForPdf {

    @Test
    @DisplayName("PDF를 PDF 변환 지원 형식으로 인식한다")
    void shouldRecognizePdfAsSupported() {
      assertThat(detector.isSupportedForPdf("application/pdf")).isTrue();
    }

    @Test
    @DisplayName("모든 Office 형식을 PDF 변환 지원 형식으로 인식한다")
    void shouldRecognizeAllOfficeFormatsAsSupported() {
      // Word
      assertThat(detector.isSupportedForPdf("application/msword")).isTrue();
      assertThat(detector.isSupportedForPdf(
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document")).isTrue();
      // Excel
      assertThat(detector.isSupportedForPdf("application/vnd.ms-excel")).isTrue();
      assertThat(detector.isSupportedForPdf(
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).isTrue();
      // PowerPoint
      assertThat(detector.isSupportedForPdf("application/vnd.ms-powerpoint")).isTrue();
      assertThat(detector.isSupportedForPdf(
          "application/vnd.openxmlformats-officedocument.presentationml.presentation")).isTrue();
    }

    @Test
    @DisplayName("OpenDocument 형식을 PDF 변환 지원 형식으로 인식한다")
    void shouldRecognizeOdfAsSupported() {
      assertThat(detector.isSupportedForPdf("application/vnd.oasis.opendocument.text")).isTrue();
      assertThat(detector.isSupportedForPdf("application/vnd.oasis.opendocument.spreadsheet")).isTrue();
      assertThat(detector.isSupportedForPdf("application/vnd.oasis.opendocument.presentation")).isTrue();
    }

    @Test
    @DisplayName("HTML을 PDF 변환 지원 형식으로 인식한다")
    void shouldRecognizeHtmlAsSupported() {
      assertThat(detector.isSupportedForPdf("text/html")).isTrue();
      assertThat(detector.isSupportedForPdf("application/xhtml+xml")).isTrue();
    }

    @Test
    @DisplayName("CSV를 PDF 변환 지원 형식으로 인식한다")
    void shouldRecognizeCsvAsSupported() {
      assertThat(detector.isSupportedForPdf("text/csv")).isTrue();
    }

    @Test
    @DisplayName("이미지는 PDF 변환 미지원이다")
    void shouldNotRecognizeImageAsSupported() {
      assertThat(detector.isSupportedForPdf("image/png")).isFalse();
      assertThat(detector.isSupportedForPdf("image/jpeg")).isFalse();
    }
  }

  @Nested
  @DisplayName("isDoclingSupported()")
  class IsDoclingSupported {

    @Test
    @DisplayName("PDF는 Docling 지원 형식이다")
    void shouldRecognizePdfAsDoclingSupported() {
      assertThat(detector.isDoclingSupported("application/pdf")).isTrue();
    }

    @Test
    @DisplayName("DOCX는 Docling 지원 형식이다")
    void shouldRecognizeDocxAsDoclingSupported() {
      assertThat(detector.isDoclingSupported(
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document")).isTrue();
    }

    @Test
    @DisplayName("HWP는 Docling 지원 형식이다")
    void shouldRecognizeHwpAsDoclingSupported() {
      assertThat(detector.isDoclingSupported("application/x-hwp")).isTrue();
      assertThat(detector.isDoclingSupported("application/vnd.hancom.hwp")).isTrue();
    }

    @Test
    @DisplayName("DOC (legacy)는 Docling 미지원 형식이다")
    void shouldNotRecognizeDocAsDoclingSupported() {
      assertThat(detector.isDoclingSupported("application/msword")).isFalse();
    }
  }

  @Nested
  @DisplayName("detectImageMimeType()")
  class DetectImageMimeType {

    @Test
    @DisplayName("PNG 이미지의 MIME 타입을 반환한다")
    void shouldReturnPngMimeType() {
      String mimeType = detector.detectImageMimeType(PNG_BYTES);
      assertThat(mimeType).isEqualTo("image/png");
    }

    @Test
    @DisplayName("JPEG 이미지의 MIME 타입을 반환한다")
    void shouldReturnJpegMimeType() {
      String mimeType = detector.detectImageMimeType(JPEG_BYTES);
      assertThat(mimeType).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("이미지가 아닌 경우 기본값 image/png를 반환한다")
    void shouldReturnDefaultForNonImage() {
      String mimeType = detector.detectImageMimeType(PDF_BYTES);
      assertThat(mimeType).isEqualTo("image/png");
    }
  }
}
