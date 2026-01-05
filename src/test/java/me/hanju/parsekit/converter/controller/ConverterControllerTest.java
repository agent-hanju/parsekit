package me.hanju.parsekit.converter.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConverterControllerTest {

  private static final Path TEST_FILES_DIR = Path.of("data/inputs");
  private static final Path OUTPUT_DIR = Path.of("data/outputs");

  @Autowired
  private MockMvc mockMvc;

  @BeforeAll
  static void setUp() throws IOException {
    Files.createDirectories(OUTPUT_DIR);
  }

  @Nested
  @DisplayName("GET /api/convert/health")
  class Health {

    @Test
    @DisplayName("헬스체크가 OK를 반환한다")
    void shouldReturnOk() throws Exception {
      mockMvc.perform(get("/api/convert/health"))
          .andExpect(status().isOk())
          .andExpect(content().string("OK"));
    }
  }

  @Nested
  @DisplayName("POST /api/convert/odt")
  class ConvertToOdt {

    @TestFactory
    @DisplayName("data/inputs 디렉토리의 모든 문서 파일을 ODT로 변환한다")
    Stream<DynamicTest> shouldConvertAllDocumentsToOdt() throws IOException {
      if (!Files.exists(TEST_FILES_DIR)) {
        return Stream.empty();
      }

      return Files.list(TEST_FILES_DIR)
          .filter(Files::isRegularFile)
          .filter(path -> isTextDocumentFile(path.getFileName().toString()))
          .map(path -> DynamicTest.dynamicTest(
              "ODT 변환: " + path.getFileName(),
              () -> {
                byte[] fileBytes = Files.readAllBytes(path);
                String filename = path.getFileName().toString();

                MockMultipartFile file = new MockMultipartFile(
                    "file", filename, "application/octet-stream", fileBytes);

                MvcResult result = mockMvc.perform(multipart("/api/convert/odt").file(file))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "application/vnd.oasis.opendocument.text"))
                    .andReturn();

                byte[] content = result.getResponse().getContentAsByteArray();
                assertThat(content).isNotEmpty();

                // 결과 파일 저장
                String outputFilename = filename.replaceAll("\\.[^.]+$", "_controller.odt");
                Files.write(OUTPUT_DIR.resolve(outputFilename), content);
              }));
    }
  }

  @Nested
  @DisplayName("POST /api/convert/pdf")
  class ConvertToPdf {

    @TestFactory
    @DisplayName("data/inputs 디렉토리의 모든 문서 파일을 PDF로 변환한다")
    Stream<DynamicTest> shouldConvertAllDocumentsToPdf() throws IOException {
      if (!Files.exists(TEST_FILES_DIR)) {
        return Stream.empty();
      }

      return Files.list(TEST_FILES_DIR)
          .filter(Files::isRegularFile)
          .filter(path -> isDocumentFile(path.getFileName().toString()))
          .map(path -> DynamicTest.dynamicTest(
              "PDF 변환: " + path.getFileName(),
              () -> {
                byte[] fileBytes = Files.readAllBytes(path);
                String filename = path.getFileName().toString();

                MockMultipartFile file = new MockMultipartFile(
                    "file", filename, "application/octet-stream", fileBytes);

                MvcResult result = mockMvc.perform(multipart("/api/convert/pdf").file(file))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "application/pdf"))
                    .andReturn();

                byte[] content = result.getResponse().getContentAsByteArray();
                assertThat(content).isNotEmpty();
                assertThat(new String(content, 0, 4)).isEqualTo("%PDF");

                // 결과 파일 저장
                String outputFilename = filename.replaceAll("\\.[^.]+$", "_controller.pdf");
                Files.write(OUTPUT_DIR.resolve(outputFilename), content);
              }));
    }
  }

  @Nested
  @DisplayName("POST /api/convert/images")
  class ConvertToImages {

    @TestFactory
    @DisplayName("data/inputs 디렉토리의 모든 PDF 파일을 이미지로 변환한다 (NDJSON)")
    Stream<DynamicTest> shouldConvertAllPdfsToImages() throws IOException {
      if (!Files.exists(TEST_FILES_DIR)) {
        return Stream.empty();
      }

      return Files.list(TEST_FILES_DIR)
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".pdf"))
          .map(path -> DynamicTest.dynamicTest(
              "이미지 변환: " + path.getFileName(),
              () -> {
                byte[] fileBytes = Files.readAllBytes(path);
                String filename = path.getFileName().toString();

                MockMultipartFile file = new MockMultipartFile(
                    "file", filename, "application/pdf", fileBytes);

                // 비동기 요청 시작
                MvcResult asyncResult = mockMvc.perform(multipart("/api/convert/images")
                    .file(file)
                    .param("format", "png")
                    .param("dpi", "150"))
                    .andExpect(request().asyncStarted())
                    .andReturn();

                // 비동기 완료 대기 후 결과 획득
                MvcResult result = mockMvc.perform(asyncDispatch(asyncResult))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "application/x-ndjson"))
                    .andReturn();

                String content = result.getResponse().getContentAsString();
                assertThat(content).isNotBlank();

                // NDJSON 형식 확인 (각 줄이 JSON)
                String[] lines = content.split("\n");
                assertThat(lines.length).isGreaterThan(0);
                for (String line : lines) {
                  assertThat(line).contains("\"page\":");
                  assertThat(line).contains("\"encoded_uri\":");
                  assertThat(line).contains("\"total_pages\":");
                }
              }));
    }
  }

  private static boolean isDocumentFile(String filename) {
    String lower = filename.toLowerCase();
    return lower.endsWith(".doc") || lower.endsWith(".docx")
        || lower.endsWith(".xls") || lower.endsWith(".xlsx")
        || lower.endsWith(".ppt") || lower.endsWith(".pptx")
        || lower.endsWith(".odt") || lower.endsWith(".ods") || lower.endsWith(".odp")
        || lower.endsWith(".rtf") || lower.endsWith(".txt")
        || lower.endsWith(".hwp") || lower.endsWith(".hwpx");
  }

  private static boolean isTextDocumentFile(String filename) {
    String lower = filename.toLowerCase();
    return lower.endsWith(".doc") || lower.endsWith(".docx")
        || lower.endsWith(".odt") || lower.endsWith(".rtf") || lower.endsWith(".txt")
        || lower.endsWith(".hwp") || lower.endsWith(".hwpx");
  }
}
