package me.hanju.parsekit.parser.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import com.fasterxml.jackson.databind.ObjectMapper;

import me.hanju.parsekit.common.exception.UnsupportedMediaTypeException;
import me.hanju.parsekit.converter.service.JodConverterService;
import me.hanju.parsekit.parser.client.DoclingClient;
import me.hanju.parsekit.parser.client.VlmClient;
import me.hanju.parsekit.parser.config.ParserProperties;
import me.hanju.parsekit.parser.dto.ParseResult;

/**
 * HybridParserController 통합 테스트
 * 컨트롤러를 직접 생성하여 테스트
 */
@SpringBootTest
@ActiveProfiles("test")
class HybridParserControllerTest {

  private static final Path TEST_FILES_DIR = Path.of("data/inputs");
  private static final Path OUTPUT_DIR = Path.of("data/outputs");

  @Autowired
  private DoclingClient doclingClient;

  @Autowired
  private VlmClient vlmClient;

  @Autowired
  private JodConverterService jodConverter;

  @Autowired
  private ParserProperties parserProperties;

  @Autowired
  private ObjectMapper objectMapper;

  private HybridParserController createController() {
    return new HybridParserController(doclingClient, vlmClient, jodConverter, parserProperties);
  }

  @BeforeAll
  static void setUp() throws IOException {
    Files.createDirectories(OUTPUT_DIR);
  }

  @Nested
  @DisplayName("parse()")
  class Parse {

    @TestFactory
    @DisplayName("data/inputs 디렉토리의 모든 문서 파일을 하이브리드 파싱한다")
    Stream<DynamicTest> shouldParseAllDocuments() throws IOException {
      if (!Files.exists(TEST_FILES_DIR)) {
        return Stream.empty();
      }

      HybridParserController controller = createController();

      return Files.list(TEST_FILES_DIR)
          .filter(Files::isRegularFile)
          .filter(path -> isDocumentFile(path.getFileName().toString()))
          .map(path -> DynamicTest.dynamicTest(
              "하이브리드 파싱: " + path.getFileName(),
              () -> {
                byte[] fileBytes = Files.readAllBytes(path);
                String filename = path.getFileName().toString();

                MockMultipartFile file = new MockMultipartFile(
                    "file", filename, "application/octet-stream", fileBytes);

                ResponseEntity<ParseResult> response = controller.parse(file, 150);

                assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().filename()).isEqualTo(filename);
                assertThat(response.getBody().markdown()).isNotBlank();

                // 결과 파일 저장
                String outputFilename = filename.replaceAll("\\.[^.]+$", "_hybrid.json");
                Files.writeString(OUTPUT_DIR.resolve(outputFilename),
                    objectMapper.writeValueAsString(response.getBody()));
              }));
    }

    @TestFactory
    @DisplayName("data/inputs 디렉토리의 모든 이미지 파일을 VLM으로 직접 OCR한다")
    Stream<DynamicTest> shouldOcrAllImages() throws IOException {
      if (!Files.exists(TEST_FILES_DIR)) {
        return Stream.empty();
      }

      HybridParserController controller = createController();

      return Files.list(TEST_FILES_DIR)
          .filter(Files::isRegularFile)
          .filter(path -> isImageFile(path.getFileName().toString()))
          .map(path -> DynamicTest.dynamicTest(
              "이미지 VLM OCR: " + path.getFileName(),
              () -> {
                byte[] fileBytes = Files.readAllBytes(path);
                String filename = path.getFileName().toString();

                MockMultipartFile file = new MockMultipartFile(
                    "file", filename, "application/octet-stream", fileBytes);

                ResponseEntity<ParseResult> response = controller.parse(file, 150);

                assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().filename()).isEqualTo(filename);

                // 결과 파일 저장
                String outputFilename = filename.replaceAll("\\.[^.]+$", "_hybrid_ocr.json");
                Files.writeString(OUTPUT_DIR.resolve(outputFilename),
                    objectMapper.writeValueAsString(response.getBody()));
              }));
    }

    @Test
    @DisplayName("플레인 텍스트 파일은 UnsupportedMediaTypeException을 던진다")
    void shouldThrowForPlainText() {
      HybridParserController controller = createController();
      MockMultipartFile file = new MockMultipartFile(
          "file", "test.txt", "text/plain", "Hello World".getBytes());

      assertThatThrownBy(() -> controller.parse(file, 150))
          .isInstanceOf(UnsupportedMediaTypeException.class)
          .hasMessageContaining("Plain text files not supported");
    }
  }

  private static boolean isDocumentFile(String filename) {
    String lower = filename.toLowerCase();
    return lower.endsWith(".pdf") || lower.endsWith(".docx")
        || lower.endsWith(".xlsx") || lower.endsWith(".pptx")
        || lower.endsWith(".hwp") || lower.endsWith(".hwpx")
        || lower.endsWith(".doc") || lower.endsWith(".xls") || lower.endsWith(".ppt");
  }

  private static boolean isImageFile(String filename) {
    String lower = filename.toLowerCase();
    return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
        || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp");
  }
}
