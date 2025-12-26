package me.hanju.parsekit;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * ConverterClient 테스트 - 생성자 및 파라미터 검증
 */
class ConverterClientTest {

  private static String converterUrl;
  private static ConverterClient converterClient;

  @BeforeAll
  static void setUp() throws IOException {
    converterUrl = resolveProperty("parsekit.converter.url");
    converterClient = new ConverterClient(WebClient.builder(), converterUrl);
  }

  private static String resolveProperty(final String key) throws IOException {
    final Properties props = new Properties();
    try (InputStream is = ConverterClientTest.class.getClassLoader().getResourceAsStream("application.properties")) {
      props.load(is);
    }
    final String value = props.getProperty(key);
    if (value != null && value.startsWith("${") && value.contains(":")) {
      final String envKey = value.substring(2, value.indexOf(':'));
      final String defaultValue = value.substring(value.indexOf(':') + 1, value.length() - 1);
      final String envValue = System.getenv(envKey);
      return envValue != null ? envValue : defaultValue;
    }
    return value;
  }

  // === 생성자 테스트 ===

  @Test
  @DisplayName("생성자 - webClientBuilder가 null이면 예외 발생")
  void constructor_nullWebClientBuilder_throwsException() {
    assertThrows(IllegalArgumentException.class,
        () -> new ConverterClient(null, converterUrl));
  }

  @Test
  @DisplayName("생성자 - baseUrl이 null이면 예외 발생")
  void constructor_nullBaseUrl_throwsException() {
    assertThrows(IllegalArgumentException.class,
        () -> new ConverterClient(WebClient.builder(), null));
  }

  @Test
  @DisplayName("생성자 - baseUrl이 빈 문자열이면 예외 발생")
  void constructor_emptyBaseUrl_throwsException() {
    assertThrows(IllegalArgumentException.class,
        () -> new ConverterClient(WebClient.builder(), ""));
  }

  @Test
  @DisplayName("생성자 - baseUrl이 공백만 있으면 예외 발생")
  void constructor_blankBaseUrl_throwsException() {
    assertThrows(IllegalArgumentException.class,
        () -> new ConverterClient(WebClient.builder(), "   "));
  }

  // === convert 메서드 파라미터 검증 테스트 ===

  @Test
  @DisplayName("convert - content가 null이면 예외 발생")
  void convert_nullContent_throwsException() {
    assertThrows(IllegalArgumentException.class,
        () -> converterClient.convert(null, "test.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
  }

  @Test
  @DisplayName("convert - content가 빈 배열이면 예외 발생")
  void convert_emptyContent_throwsException() {
    assertThrows(IllegalArgumentException.class,
        () -> converterClient.convert(new byte[0], "test.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
  }

  @Test
  @DisplayName("convert - filename이 null이면 예외 발생")
  void convert_nullFilename_throwsException() {
    assertThrows(IllegalArgumentException.class,
        () -> converterClient.convert(new byte[]{1, 2, 3}, null, "application/pdf"));
  }

  @Test
  @DisplayName("convert - filename이 빈 문자열이면 예외 발생")
  void convert_emptyFilename_throwsException() {
    assertThrows(IllegalArgumentException.class,
        () -> converterClient.convert(new byte[]{1, 2, 3}, "", "application/pdf"));
  }

  @Test
  @DisplayName("convert - filename이 공백만 있으면 예외 발생")
  void convert_blankFilename_throwsException() {
    assertThrows(IllegalArgumentException.class,
        () -> converterClient.convert(new byte[]{1, 2, 3}, "   ", "application/pdf"));
  }

  // === convertRaw 메서드 파라미터 검증 테스트 ===

  @Test
  @DisplayName("convertRaw - content가 null이면 예외 발생")
  void convertRaw_nullContent_throwsException() {
    assertThrows(IllegalArgumentException.class,
        () -> converterClient.convertRaw(null, "test.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
  }

  @Test
  @DisplayName("convertRaw - content가 빈 배열이면 예외 발생")
  void convertRaw_emptyContent_throwsException() {
    assertThrows(IllegalArgumentException.class,
        () -> converterClient.convertRaw(new byte[0], "test.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
  }

  @Test
  @DisplayName("convertRaw - filename이 null이면 예외 발생")
  void convertRaw_nullFilename_throwsException() {
    assertThrows(IllegalArgumentException.class,
        () -> converterClient.convertRaw(new byte[]{1, 2, 3}, null, "application/pdf"));
  }

  @Test
  @DisplayName("convertRaw - filename이 빈 문자열이면 예외 발생")
  void convertRaw_emptyFilename_throwsException() {
    assertThrows(IllegalArgumentException.class,
        () -> converterClient.convertRaw(new byte[]{1, 2, 3}, "", "application/pdf"));
  }

  // === convertToImages 메서드 파라미터 검증 테스트 ===

  @Test
  @DisplayName("convertToImages - content가 null이면 예외 발생")
  void convertToImages_nullContent_throwsException() {
    assertThrows(IllegalArgumentException.class,
        () -> converterClient.convertToImages(null, "test.pdf", "application/pdf", "png", 150));
  }

  @Test
  @DisplayName("convertToImages - content가 빈 배열이면 예외 발생")
  void convertToImages_emptyContent_throwsException() {
    assertThrows(IllegalArgumentException.class,
        () -> converterClient.convertToImages(new byte[0], "test.pdf", "application/pdf", "png", 150));
  }

  @Test
  @DisplayName("convertToImages - filename이 null이면 예외 발생")
  void convertToImages_nullFilename_throwsException() {
    assertThrows(IllegalArgumentException.class,
        () -> converterClient.convertToImages(new byte[]{1, 2, 3}, null, "application/pdf", "png", 150));
  }

  @Test
  @DisplayName("convertToImages - filename이 빈 문자열이면 예외 발생")
  void convertToImages_emptyFilename_throwsException() {
    assertThrows(IllegalArgumentException.class,
        () -> converterClient.convertToImages(new byte[]{1, 2, 3}, "", "application/pdf", "png", 150));
  }

  @Test
  @DisplayName("convertToImages - filename이 공백만 있으면 예외 발생")
  void convertToImages_blankFilename_throwsException() {
    assertThrows(IllegalArgumentException.class,
        () -> converterClient.convertToImages(new byte[]{1, 2, 3}, "   ", "application/pdf", "png", 150));
  }

  // === isAvailable 테스트 ===

  @Test
  @DisplayName("서비스 가용성 확인")
  void isAvailable_check() {
    final boolean available = converterClient.isAvailable();
    System.out.println("Converter 서비스 가용 여부: " + available);
  }
}
