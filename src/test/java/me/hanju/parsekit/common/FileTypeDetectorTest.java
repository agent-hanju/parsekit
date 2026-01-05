package me.hanju.parsekit.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import me.hanju.parsekit.common.FileTypeDetector.FileCategory;
import me.hanju.parsekit.common.FileTypeDetector.FileTypeInfo;

class FileTypeDetectorTest {

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

  @Nested
  @DisplayName("detect()")
  class Detect {

    @Test
    @DisplayName("PNG 이미지 파일 정보를 감지한다")
    void shouldDetectPngFileInfo() {
      MockMultipartFile file = new MockMultipartFile(
          "file", "test.png", "image/png", PNG_BYTES);

      FileTypeInfo info = FileTypeDetector.detect(file);

      assertThat(info.mimeType()).isEqualTo("image/png");
      assertThat(info.category()).isEqualTo(FileCategory.IMAGE);
      assertThat(info.extension()).isEqualTo(".png");
      assertThat(info.originalFilename()).isEqualTo("test.png");
      assertThat(info.baseFilename()).isEqualTo("test");
    }

    @Test
    @DisplayName("JPEG 이미지 파일 정보를 감지한다")
    void shouldDetectJpegFileInfo() {
      MockMultipartFile file = new MockMultipartFile(
          "file", "photo.jpg", "image/jpeg", JPEG_BYTES);

      FileTypeInfo info = FileTypeDetector.detect(file);

      assertThat(info.mimeType()).isEqualTo("image/jpeg");
      assertThat(info.category()).isEqualTo(FileCategory.IMAGE);
      assertThat(info.extension()).isEqualTo(".jpg");
      assertThat(info.originalFilename()).isEqualTo("photo.jpg");
      assertThat(info.baseFilename()).isEqualTo("photo");
    }

    @Test
    @DisplayName("PDF 문서 파일 정보를 감지한다")
    void shouldDetectPdfFileInfo() {
      MockMultipartFile file = new MockMultipartFile(
          "file", "document.pdf", "application/pdf", PDF_BYTES);

      FileTypeInfo info = FileTypeDetector.detect(file);

      assertThat(info.mimeType()).isEqualTo("application/pdf");
      assertThat(info.category()).isEqualTo(FileCategory.PDF);
      assertThat(info.extension()).isEqualTo(".pdf");
      assertThat(info.originalFilename()).isEqualTo("document.pdf");
      assertThat(info.baseFilename()).isEqualTo("document");
    }

    @Test
    @DisplayName("텍스트 파일 정보를 감지한다")
    void shouldDetectTextFileInfo() {
      byte[] textBytes = "Hello, World!".getBytes();
      MockMultipartFile file = new MockMultipartFile(
          "file", "readme.txt", "text/plain", textBytes);

      FileTypeInfo info = FileTypeDetector.detect(file);

      assertThat(info.mimeType()).isEqualTo("text/plain");
      assertThat(info.category()).isEqualTo(FileCategory.PLAIN_TEXT);
      assertThat(info.extension()).isEqualTo(".txt");
      assertThat(info.originalFilename()).isEqualTo("readme.txt");
      assertThat(info.baseFilename()).isEqualTo("readme");
    }

    @Test
    @DisplayName("확장자가 없는 파일의 baseFilename을 처리한다")
    void shouldHandleFilenameWithoutExtension() {
      byte[] textBytes = "Hello".getBytes();
      MockMultipartFile file = new MockMultipartFile(
          "file", "README", "text/plain", textBytes);

      FileTypeInfo info = FileTypeDetector.detect(file);

      assertThat(info.originalFilename()).isEqualTo("README");
      assertThat(info.baseFilename()).isEqualTo("README");
    }

    @Test
    @DisplayName("여러 점이 있는 파일명을 처리한다")
    void shouldHandleFilenameWithMultipleDots() {
      MockMultipartFile file = new MockMultipartFile(
          "file", "my.document.v2.pdf", "application/pdf", PDF_BYTES);

      FileTypeInfo info = FileTypeDetector.detect(file);

      assertThat(info.originalFilename()).isEqualTo("my.document.v2.pdf");
      assertThat(info.baseFilename()).isEqualTo("my.document.v2");
    }
  }

  @Nested
  @DisplayName("classify via detect()")
  class Classify {

    @Test
    @DisplayName("PDF 파일을 PDF로 분류한다")
    void shouldClassifyPdf() {
      MockMultipartFile file = new MockMultipartFile(
          "file", "test.pdf", "application/pdf", PDF_BYTES);
      assertThat(FileTypeDetector.detect(file).category()).isEqualTo(FileCategory.PDF);
    }

    @Test
    @DisplayName("이미지 파일을 IMAGE로 분류한다")
    void shouldClassifyImage() {
      MockMultipartFile pngFile = new MockMultipartFile(
          "file", "test.png", "image/png", PNG_BYTES);
      assertThat(FileTypeDetector.detect(pngFile).category()).isEqualTo(FileCategory.IMAGE);

      MockMultipartFile jpegFile = new MockMultipartFile(
          "file", "test.jpg", "image/jpeg", JPEG_BYTES);
      assertThat(FileTypeDetector.detect(jpegFile).category()).isEqualTo(FileCategory.IMAGE);
    }

    @Test
    @DisplayName("플레인 텍스트를 PLAIN_TEXT로 분류한다")
    void shouldClassifyPlainText() {
      byte[] textBytes = "Hello, World!".getBytes();
      MockMultipartFile file = new MockMultipartFile(
          "file", "test.txt", "text/plain", textBytes);
      assertThat(FileTypeDetector.detect(file).category()).isEqualTo(FileCategory.PLAIN_TEXT);
    }

    @Test
    @DisplayName("Word 문서를 DOCUMENT로 분류한다")
    void shouldClassifyWordAsDocument() {
      // DOCX 파일의 magic bytes (PK zip header)
      byte[] docxBytes = new byte[] { 0x50, 0x4B, 0x03, 0x04 };
      MockMultipartFile file = new MockMultipartFile(
          "file", "test.docx",
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          docxBytes);
      assertThat(FileTypeDetector.detect(file).category()).isEqualTo(FileCategory.DOCUMENT);
    }

    @Test
    @DisplayName("Excel 문서를 SPREADSHEET로 분류한다")
    void shouldClassifyExcelAsSpreadsheet() {
      byte[] xlsxBytes = new byte[] { 0x50, 0x4B, 0x03, 0x04 };
      MockMultipartFile file = new MockMultipartFile(
          "file", "test.xlsx",
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          xlsxBytes);
      assertThat(FileTypeDetector.detect(file).category()).isEqualTo(FileCategory.SPREADSHEET);
    }

    @Test
    @DisplayName("PowerPoint 문서를 PRESENTATION으로 분류한다")
    void shouldClassifyPptAsPresentation() {
      byte[] pptxBytes = new byte[] { 0x50, 0x4B, 0x03, 0x04 };
      MockMultipartFile file = new MockMultipartFile(
          "file", "test.pptx",
          "application/vnd.openxmlformats-officedocument.presentationml.presentation",
          pptxBytes);
      assertThat(FileTypeDetector.detect(file).category()).isEqualTo(FileCategory.PRESENTATION);
    }

    @Test
    @DisplayName("알 수 없는 타입은 UnsupportedMediaTypeException을 던진다")
    void shouldThrowExceptionForUnknownType() {
      byte[] unknownBytes = new byte[] { 0x00, 0x01, 0x02, 0x03 };
      MockMultipartFile file = new MockMultipartFile(
          "file", "test.xyz", "application/unknown-type", unknownBytes);
      assertThatThrownBy(() -> FileTypeDetector.detect(file))
          .isInstanceOf(me.hanju.parsekit.common.exception.UnsupportedMediaTypeException.class)
          .hasMessageContaining("Unsupported MIME type");
    }
  }

  @Nested
  @DisplayName("toBase64EncodedUri()")
  class ToBase64EncodedUri {

    @Test
    @DisplayName("MIME 타입과 바이트 배열로 data URI를 생성한다")
    void shouldCreateDataUri() {
      byte[] data = "Hello".getBytes();
      String result = FileTypeDetector.toBase64EncodedUri("text/plain", data);

      assertThat(result).startsWith("data:text/plain;base64,");
      assertThat(result).isEqualTo("data:text/plain;base64," + Base64.getEncoder().encodeToString(data));
    }

    @Test
    @DisplayName("이미지 MIME 타입으로 data URI를 생성한다")
    void shouldCreateImageDataUri() {
      String result = FileTypeDetector.toBase64EncodedUri("image/png", PNG_BYTES);

      assertThat(result).startsWith("data:image/png;base64,");
      assertThat(FileTypeDetector.validateBase64EncodedUri(result)).isTrue();
    }
  }

  @Nested
  @DisplayName("validateBase64EncodedUri()")
  class ValidateBase64EncodedUri {

    @Test
    @DisplayName("유효한 data URI를 검증한다")
    void shouldValidateCorrectDataUri() {
      String validUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(PNG_BYTES);
      assertThat(FileTypeDetector.validateBase64EncodedUri(validUri)).isTrue();
    }

    @Test
    @DisplayName("null은 유효하지 않다")
    void shouldRejectNull() {
      assertThat(FileTypeDetector.validateBase64EncodedUri(null)).isFalse();
    }

    @Test
    @DisplayName("data: 접두사가 없으면 유효하지 않다")
    void shouldRejectWithoutDataPrefix() {
      assertThat(FileTypeDetector.validateBase64EncodedUri("image/png;base64,abc")).isFalse();
    }

    @Test
    @DisplayName(";base64, 구분자가 없으면 유효하지 않다")
    void shouldRejectWithoutBase64Delimiter() {
      assertThat(FileTypeDetector.validateBase64EncodedUri("data:image/png,abc")).isFalse();
    }

    @Test
    @DisplayName("잘못된 base64 데이터는 유효하지 않다")
    void shouldRejectInvalidBase64() {
      assertThat(FileTypeDetector.validateBase64EncodedUri("data:image/png;base64,!!!")).isFalse();
    }
  }

  @Nested
  @DisplayName("decodeBase64()")
  class DecodeBase64 {

    @Test
    @DisplayName("base64 문자열을 디코딩한다")
    void shouldDecodeBase64String() {
      String original = "Hello, World!";
      String encoded = Base64.getEncoder().encodeToString(original.getBytes());

      byte[] decoded = FileTypeDetector.decodeBase64(encoded);

      assertThat(new String(decoded)).isEqualTo(original);
    }

    @Test
    @DisplayName("null은 IllegalArgumentException을 던진다")
    void shouldThrowForNull() {
      assertThatThrownBy(() -> FileTypeDetector.decodeBase64(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("cannot be blank");
    }

    @Test
    @DisplayName("빈 문자열은 IllegalArgumentException을 던진다")
    void shouldThrowForBlank() {
      assertThatThrownBy(() -> FileTypeDetector.decodeBase64("   "))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("cannot be blank");
    }
  }

  @Nested
  @DisplayName("decodeBase64FromUri()")
  class DecodeBase64FromUri {

    @Test
    @DisplayName("data URI에서 바이트 배열을 추출한다")
    void shouldDecodeFromDataUri() {
      String dataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(PNG_BYTES);

      byte[] decoded = FileTypeDetector.decodeBase64FromUri(dataUri);

      assertThat(decoded).isEqualTo(PNG_BYTES);
    }

    @Test
    @DisplayName("잘못된 URI 형식은 IllegalArgumentException을 던진다")
    void shouldThrowForInvalidUri() {
      assertThatThrownBy(() -> FileTypeDetector.decodeBase64FromUri("invalid-uri"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid base64 encoded URI");
    }
  }

  @Nested
  @DisplayName("getBytes()")
  class GetBytes {

    @Test
    @DisplayName("MultipartFile에서 바이트 배열을 읽는다")
    void shouldGetBytesFromMultipartFile() {
      MockMultipartFile file = new MockMultipartFile(
          "file", "test.png", "image/png", PNG_BYTES);

      byte[] result = FileTypeDetector.getBytes(file);

      assertThat(result).isEqualTo(PNG_BYTES);
    }

    @Test
    @DisplayName("빈 파일에서도 바이트 배열을 읽는다")
    void shouldGetEmptyBytesFromEmptyFile() {
      MockMultipartFile file = new MockMultipartFile(
          "file", "empty.txt", "text/plain", new byte[0]);

      byte[] result = FileTypeDetector.getBytes(file);

      assertThat(result).isEmpty();
    }
  }
}
