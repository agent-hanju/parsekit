package me.hanju.parsekit.parser.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * HybridParserController 통합 테스트
 * Docling + VLM 모두 활성화된 환경에서 테스트
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HybridParserControllerTest {

  private static final Path TEST_FILES_DIR = Path.of("data/inputs");
  private static final Path OUTPUT_DIR = Path.of("data/outputs");

  @Autowired
  private MockMvc mockMvc;

  @BeforeAll
  static void setUp() throws IOException {
    Files.createDirectories(OUTPUT_DIR);
  }

  @Nested
  @DisplayName("POST /api/parse/parse")
  class Parse {

    @TestFactory
    @DisplayName("data/inputs 디렉토리의 모든 문서 파일을 하이브리드 파싱한다")
    Stream<DynamicTest> shouldParseAllDocuments() throws IOException {
      if (!Files.exists(TEST_FILES_DIR)) {
        return Stream.empty();
      }

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

                MvcResult result = mockMvc.perform(multipart("/api/parse/parse")
                    .file(file)
                    .param("dpi", "150"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.filename").value(filename))
                    .andExpect(jsonPath("$.markdown").isNotEmpty())
                    .andReturn();

                String content = result.getResponse().getContentAsString();
                assertThat(content).contains("markdown");

                // 결과 파일 저장
                String outputFilename = filename.replaceAll("\\.[^.]+$", "_hybrid.json");
                Files.writeString(OUTPUT_DIR.resolve(outputFilename), content);
              }));
    }

    @TestFactory
    @DisplayName("data/inputs 디렉토리의 모든 이미지 파일을 VLM으로 OCR한다")
    Stream<DynamicTest> shouldOcrAllImages() throws IOException {
      if (!Files.exists(TEST_FILES_DIR)) {
        return Stream.empty();
      }

      return Files.list(TEST_FILES_DIR)
          .filter(Files::isRegularFile)
          .filter(path -> isImageFile(path.getFileName().toString()))
          .map(path -> DynamicTest.dynamicTest(
              "이미지 OCR: " + path.getFileName(),
              () -> {
                byte[] fileBytes = Files.readAllBytes(path);
                String filename = path.getFileName().toString();

                MockMultipartFile file = new MockMultipartFile(
                    "file", filename, "application/octet-stream", fileBytes);

                MvcResult result = mockMvc.perform(multipart("/api/parse/parse")
                    .file(file)
                    .param("dpi", "150"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.filename").value(filename))
                    .andExpect(jsonPath("$.markdown").isNotEmpty())
                    .andReturn();

                String content = result.getResponse().getContentAsString();
                assertThat(content).contains("markdown");

                // 결과 파일 저장
                String outputFilename = filename.replaceAll("\\.[^.]+$", "_hybrid_ocr.json");
                Files.writeString(OUTPUT_DIR.resolve(outputFilename), content);
              }));
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
