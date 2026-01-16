package me.hanju.parsekit.common;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

import org.apache.tika.config.TikaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.springframework.web.multipart.MultipartFile;

import me.hanju.parsekit.common.exception.ParseKitException;
import me.hanju.parsekit.common.exception.UnsupportedMediaTypeException;

/**
 * 파일 타입 감지 유틸리티 클래스.
 * Apache Tika를 사용하여 MIME 타입을 감지하고 파일을 분류한다.
 */
public final class FileTypeDetector {

  /**
   * 파일 타입 감지 결과.
   *
   * @param mimeType         감지된 MIME 타입 (예: "application/pdf")
   * @param category         파일 카테고리
   * @param extension        감지된 확장자 (예: ".pdf"), 알 수 없으면 빈 문자열
   * @param originalFilename 원본 파일명 (예: "document.pdf")
   * @param baseFilename     확장자를 제거한 파일명 (예: "document")
   */
  public record FileTypeInfo(
      String mimeType,
      FileCategory category,
      String extension,
      String originalFilename,
      String baseFilename) {
  }

  /**
   * 파일 카테고리 분류.
   * 지원되지 않는 타입은 UnsupportedMediaTypeException으로 처리된다.
   */
  public enum FileCategory {
    /** 도큐먼트 (Word, HWP, HTML 등 - 플레인텍스트, 마크다운 제외) */
    DOCUMENT,
    /** 스프레드시트 (Excel, ODS, CSV 등) */
    SPREADSHEET,
    /** 프레젠테이션 (PowerPoint, ODP 등) */
    PRESENTATION,
    /** PDF */
    PDF,
    /** 이미지 (PNG, JPEG, GIF 등) */
    IMAGE,
    /** 플레인 텍스트 */
    PLAIN_TEXT,
    /** 마크다운 */
    MARKDOWN
  }

  private static final TikaConfig TIKA_CONFIG;
  private static final Detector DETECTOR;
  private static final MimeTypes MIME_TYPES;

  static {
    try {
      TIKA_CONFIG = new TikaConfig();
      DETECTOR = TIKA_CONFIG.getDetector();
      MIME_TYPES = TIKA_CONFIG.getMimeRepository();
    } catch (Exception e) {
      throw new ExceptionInInitializerError("Failed to initialize TikaConfig: " + e.getMessage());
    }
  }

  // 도큐먼트 계열(플레인텍스트, Markdown 미포함)
  private static final Set<String> DOCUMENT_TYPES = Set.of(
      // Microsoft Word
      "application/msword", // .doc
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // .docx
      "application/vnd.ms-word.template.macroEnabled.12", // .dotm
      "application/vnd.openxmlformats-officedocument.wordprocessingml.template", // .dotx
      // OpenDocument Text
      "application/vnd.oasis.opendocument.text", // .odt
      "application/vnd.oasis.opendocument.text-template", // .ott
      "application/vnd.oasis.opendocument.text-flat-xml", // .font
      // 한컴오피스 한글
      "application/x-hwp", // .hwp
      "application/hwp", // .hwp (alternative)
      "application/vnd.hancom.hwp", // .hwp (alternative)
      "application/vnd.hancom.hwpx", // .hwpx
      "application/hwp+zip", // .hwpx (alternative)
      // RTF
      "text/rtf", // .rtf
      "application/rtf", // .rtf (alternative)
      // HTML
      "text/html", // .html, .htm
      "application/xhtml+xml", // .xhtml
      // WordPerfect
      "application/vnd.wordperfect", // .wpd
      // AbiWord
      "application/x-abiword" // .abw
  );

  // 스프레드시트 계열
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

  // 프레젠테이션 계열
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

  // 플레인 텍스트
  private static final Set<String> PLAIN_TEXT_TYPES = Set.of(
      "text/plain");

  // 마크다운
  private static final Set<String> MARKDOWN_TYPES = Set.of(
      "text/markdown",
      "text/x-markdown");

  // 확장자 -> MIME 타입 매핑 (Tika가 감지 못할 때 fallback용)
  private static final Map<String, String> EXTENSION_MIME_MAP = Map.ofEntries(
      // Documents
      Map.entry(".hwp", "application/x-hwp"),
      Map.entry(".hwpx", "application/vnd.hancom.hwpx"),
      Map.entry(".doc", "application/msword"),
      Map.entry(".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
      Map.entry(".odt", "application/vnd.oasis.opendocument.text"),
      Map.entry(".rtf", "text/rtf"),
      Map.entry(".html", "text/html"),
      Map.entry(".htm", "text/html"),
      // Spreadsheets
      Map.entry(".xls", "application/vnd.ms-excel"),
      Map.entry(".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
      Map.entry(".ods", "application/vnd.oasis.opendocument.spreadsheet"),
      Map.entry(".csv", "text/csv"),
      // Presentations
      Map.entry(".ppt", "application/vnd.ms-powerpoint"),
      Map.entry(".pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
      Map.entry(".odp", "application/vnd.oasis.opendocument.presentation"),
      // PDF
      Map.entry(".pdf", "application/pdf"),
      // Images
      Map.entry(".png", "image/png"),
      Map.entry(".jpg", "image/jpeg"),
      Map.entry(".jpeg", "image/jpeg"),
      Map.entry(".gif", "image/gif"),
      Map.entry(".bmp", "image/bmp"),
      Map.entry(".webp", "image/webp"),
      Map.entry(".tiff", "image/tiff"),
      Map.entry(".tif", "image/tiff"),
      // Plain text
      Map.entry(".txt", "text/plain"),
      // Markdown
      Map.entry(".md", "text/markdown"),
      Map.entry(".markdown", "text/markdown"));

  private static final Logger log = LoggerFactory.getLogger(FileTypeDetector.class);

  private static final String BASE64_DELIMITER = ";base64,";

  private FileTypeDetector() {
  }

  private static MediaType detectMimeType(final byte[] content, final String filename) {
    final Metadata metadata = new Metadata();
    if (filename != null) {
      metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
    }

    try (final InputStream stream = content != null
        ? TikaInputStream.get(new ByteArrayInputStream(content))
        : TikaInputStream.get(new ByteArrayInputStream(new byte[0]))) {
      return DETECTOR.detect(stream, metadata);
    } catch (final IOException e) {
      throw new ParseKitException("Failed to detect file type", e);
    }
  }

  private static FileCategory classify(final String mimeType) {
    if (mimeType == null || mimeType.isBlank()) {
      throw new IllegalStateException("mimeType cannot be blank");
    }
    if (PLAIN_TEXT_TYPES.contains(mimeType)) {
      return FileCategory.PLAIN_TEXT;
    }
    if (MARKDOWN_TYPES.contains(mimeType)) {
      return FileCategory.MARKDOWN;
    }
    if (DOCUMENT_TYPES.contains(mimeType)) {
      return FileCategory.DOCUMENT;
    }
    if (SPREADSHEET_TYPES.contains(mimeType)) {
      return FileCategory.SPREADSHEET;
    }
    if (PRESENTATION_TYPES.contains(mimeType)) {
      return FileCategory.PRESENTATION;
    }
    if (PDF_TYPES.contains(mimeType)) {
      return FileCategory.PDF;
    }
    if (IMAGE_TYPES.contains(mimeType)) {
      return FileCategory.IMAGE;
    }
    throw new UnsupportedMediaTypeException("Unsupported MIME type: " + mimeType);
  }

  private static String detectExtension(final String mimeType) {
    if (mimeType == null || mimeType.isBlank()) {
      throw new IllegalStateException("mimeType cannot be blank");
    } else {
      try {
        final MimeType type = MIME_TYPES.forName(mimeType);
        return type.getExtension();
      } catch (MimeTypeException e) {
        throw new IllegalStateException("mimeType is invalid");
      }
    }
  }

  private static String extractBaseFilename(final String filename) {
    if (filename == null || filename.isBlank()) {
      throw new IllegalStateException("filename cannot be blank");
    } else {
      final int lastDotIndex = filename.lastIndexOf('.');
      if (lastDotIndex < 1) {
        // 파일 이름에 '.'이 없거나, 맨 처음부터 등장하는 경우 그대로 사용
        return filename;
      } else {
        return filename.substring(0, lastDotIndex);
      }
    }
  }

  /**
   * byte 배열과 파일명에서 파일 타입 정보를 감지한다.
   * MIME 타입, 카테고리, 확장자, 파일명 정보를 한 번에 반환한다.
   * Tika가 분류할 수 없는 MIME 타입을 반환하면 파일 확장자 기반으로 fallback한다.
   *
   * @param content  파일 내용
   * @param filename 파일명
   * @return 파일 타입 정보
   */
  public static FileTypeInfo detect(final byte[] content, final String filename) {
    final String originalFilename = filename;
    final String baseFilename = extractBaseFilename(originalFilename);

    String mimeType = detectMimeType(content, originalFilename).toString();

    // Tika가 분류할 수 없는 MIME 타입이면 확장자 기반으로 fallback
    if (!isClassifiable(mimeType)) {
      final String fileExtension = extractExtension(originalFilename);
      final String fallbackMimeType = fileExtension != null
          ? EXTENSION_MIME_MAP.get(fileExtension.toLowerCase())
          : null;

      if (fallbackMimeType != null) {
        log.warn("Tika detected '{}' for file '{}', falling back to extension-based MIME type: {}",
            mimeType, originalFilename, fallbackMimeType);
        mimeType = fallbackMimeType;
      }
    }

    final FileCategory category = classify(mimeType);
    final String extension = detectExtension(mimeType);

    return new FileTypeInfo(mimeType, category, extension, originalFilename, baseFilename);
  }

  /**
   * MultipartFile에서 파일 타입 정보를 감지한다.
   * MIME 타입, 카테고리, 확장자, 파일명 정보를 한 번에 반환한다.
   * Tika가 분류할 수 없는 MIME 타입을 반환하면 파일 확장자 기반으로 fallback한다.
   */
  public static FileTypeInfo detect(final MultipartFile file) {
    return detect(getBytes(file), file.getOriginalFilename());
  }

  private static String extractExtension(final String filename) {
    if (filename == null || filename.isBlank()) {
      return null;
    }
    final int lastDotIndex = filename.lastIndexOf('.');
    if (lastDotIndex < 0 || lastDotIndex == filename.length() - 1) {
      return null;
    }
    return filename.substring(lastDotIndex);
  }

  private static boolean isClassifiable(final String mimeType) {
    return PLAIN_TEXT_TYPES.contains(mimeType)
        || MARKDOWN_TYPES.contains(mimeType)
        || DOCUMENT_TYPES.contains(mimeType)
        || SPREADSHEET_TYPES.contains(mimeType)
        || PRESENTATION_TYPES.contains(mimeType)
        || PDF_TYPES.contains(mimeType)
        || IMAGE_TYPES.contains(mimeType);
  }

  /**
   * MultipartFile으로 받은 파일을 Base64 encoded uri로 변경한다.
   *
   * @param file
   * @return Base64 Encoded String
   */
  public static String toBase64EncodedUri(final MultipartFile file) {
    final byte[] bytes = getBytes(file);
    return toBase64EncodedUri(detect(bytes, file.getOriginalFilename()).mimeType(), bytes);
  }

  /**
   * MIME-Type과 파일 byte를 받아 Base64 Encoded uri로 변경한다.
   *
   * @param mimeType
   * @param bytes
   * @return Base64 Encoded String
   */
  public static String toBase64EncodedUri(final String mimeType, final byte[] bytes) {
    final String bas64Encoded = Base64.getEncoder().encodeToString(bytes);
    return String.format("data:%s;base64,%s", mimeType, bas64Encoded);
  }

  public static boolean validateBase64EncodedUri(final String base64EncodedUri) {
    if (base64EncodedUri == null
        || !base64EncodedUri.startsWith("data:")
        || !base64EncodedUri.contains(BASE64_DELIMITER)) {
      return false;
    } else {
      final String[] parts = base64EncodedUri.split(BASE64_DELIMITER);
      if (parts.length != 2) {
        return false;
      } else {
        try {
          Base64.getDecoder().decode(parts[1]);
          return true;
        } catch (final IllegalArgumentException e) {
          return false;
        }
      }
    }
  }

  /**
   * Base64 문자열을 디코딩한다.
   *
   * @param base64Data Base64로 인코딩된 문자열
   * @return 디코딩된 바이트 배열
   * @throws IllegalArgumentException Base64 형식이 유효하지 않은 경우
   */
  public static byte[] decodeBase64(final String base64Data) {
    if (base64Data == null || base64Data.isBlank()) {
      throw new IllegalArgumentException("base64Data cannot be blank");
    }
    return Base64.getDecoder().decode(base64Data);
  }

  /**
   * Base64 Encoded URI에서 바이트 배열을 추출한다.
   * 형식: data:{mimeType};base64,{base64Data}
   *
   * @param base64EncodedUri Base64 인코딩된 데이터 URI
   * @return 디코딩된 바이트 배열
   * @throws IllegalArgumentException URI 형식이 유효하지 않은 경우
   */
  public static byte[] decodeBase64FromUri(final String base64EncodedUri) {
    if (!validateBase64EncodedUri(base64EncodedUri)) {
      throw new IllegalArgumentException("Invalid base64 encoded URI format");
    }
    final String[] parts = base64EncodedUri.split(BASE64_DELIMITER);
    return Base64.getDecoder().decode(parts[1]);
  }

  /**
   * MultipartFile에서 바이트 배열을 읽는다.
   *
   * @param file 읽을 MultipartFile
   * @return 파일의 바이트 배열
   * @throws ParseKitException 파일 읽기 실패 시
   */
  public static byte[] getBytes(final MultipartFile file) {
    try {
      return file.getBytes();
    } catch (final IOException e) {
      throw new ParseKitException("Failed to read uploaded file", e);
    }
  }
}
