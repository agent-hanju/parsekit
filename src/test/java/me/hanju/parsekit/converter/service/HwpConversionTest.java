package me.hanju.parsekit.converter.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.jodconverter.core.DocumentConverter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * HWP 파일 변환 전용 테스트
 * LibreOffice HWP 확장 설치 필요
 * 타임아웃이 길어서 별도 분리
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("slow")
class HwpConversionTest {

  private static final Path TEST_FILES_DIR = Path.of("data/inputs");
  private static final Path OUTPUT_DIR = Path.of("data/outputs");

  private static JodConverterService service;

  @BeforeAll
  static void setUp(@Autowired DocumentConverter documentConverter) throws IOException {
    service = new JodConverterService(documentConverter);
    Files.createDirectories(OUTPUT_DIR);
  }

  @Test
  @DisplayName("HWP 파일을 PDF로 변환한다")
  @Timeout(value = 5, unit = TimeUnit.MINUTES)
  void shouldConvertHwpToPdf() throws IOException {
    Path hwpFile = TEST_FILES_DIR.resolve("sample.hwp");
    if (!Files.exists(hwpFile)) {
      System.out.println("sample.hwp not found, skipping test");
      return;
    }

    byte[] fileBytes = Files.readAllBytes(hwpFile);
    System.out.println("Starting HWP to PDF conversion...");
    long start = System.currentTimeMillis();

    byte[] result = service.convertToPdf(fileBytes);

    long elapsed = System.currentTimeMillis() - start;
    System.out.println("Conversion completed in " + elapsed + "ms");

    assertThat(result).isNotEmpty();
    assertThat(new String(result, 0, 4)).isEqualTo("%PDF");

    // 결과 파일 저장
    Files.write(OUTPUT_DIR.resolve("sample_hwp.pdf"), result);
    System.out.println("Saved to data/outputs/sample_hwp.pdf");
  }

  @Test
  @DisplayName("HWPX 파일을 PDF로 변환한다")
  @Timeout(value = 5, unit = TimeUnit.MINUTES)
  void shouldConvertHwpxToPdf() throws IOException {
    Path hwpxFile = TEST_FILES_DIR.resolve("sample.hwpx");
    if (!Files.exists(hwpxFile)) {
      System.out.println("sample.hwpx not found, skipping test");
      return;
    }

    byte[] fileBytes = Files.readAllBytes(hwpxFile);
    System.out.println("Starting HWPX to PDF conversion...");
    long start = System.currentTimeMillis();

    byte[] result = service.convertToPdf(fileBytes);

    long elapsed = System.currentTimeMillis() - start;
    System.out.println("Conversion completed in " + elapsed + "ms");

    assertThat(result).isNotEmpty();
    assertThat(new String(result, 0, 4)).isEqualTo("%PDF");

    // 결과 파일 저장
    Files.write(OUTPUT_DIR.resolve("sample_hwpx.pdf"), result);
    System.out.println("Saved to data/outputs/sample_hwpx.pdf");
  }
}
