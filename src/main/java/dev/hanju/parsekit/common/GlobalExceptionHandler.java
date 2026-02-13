package dev.hanju.parsekit.common;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import dev.hanju.parsekit.common.exception.BadRequestException;
import dev.hanju.parsekit.common.exception.ParseKitException;
import dev.hanju.parsekit.common.exception.UnsupportedMediaTypeException;
import dev.hanju.parsekit.converter.exception.JodConverterException;
import dev.hanju.parsekit.converter.exception.PopplerConverterException;
import dev.hanju.parsekit.parser.exception.DoclingClientException;
import dev.hanju.parsekit.parser.exception.TikaParserException;
import dev.hanju.parsekit.parser.exception.VlmClientException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final String TYPE_BASE = "https://parsekit.dev/errors/";

  private ProblemDetail createProblemDetail(HttpStatus status, String type, String title, String detail) {
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
    problemDetail.setType(URI.create(TYPE_BASE + type));
    problemDetail.setTitle(title);
    return problemDetail;
  }

  @ExceptionHandler(JodConverterException.class)
  public ProblemDetail handleJodConverterException(JodConverterException e) {
    log.error("JodConverter error", e);
    return createProblemDetail(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "conversion-failed",
        "Conversion Failed",
        e.getMessage());
  }

  @ExceptionHandler(PopplerConverterException.class)
  public ProblemDetail handlePopplerConverterException(PopplerConverterException e) {
    log.error("Poppler error", e);
    return createProblemDetail(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "image-conversion-failed",
        "Image Conversion Failed",
        e.getMessage());
  }

  @ExceptionHandler(DoclingClientException.class)
  public ProblemDetail handleDoclingClientException(DoclingClientException e) {
    log.error("Docling client error", e);
    return createProblemDetail(
        HttpStatus.BAD_GATEWAY,
        "docling-error",
        "Docling Service Error",
        e.getMessage());
  }

  @ExceptionHandler(VlmClientException.class)
  public ProblemDetail handleVlmClientException(VlmClientException e) {
    log.error("VLM client error", e);
    return createProblemDetail(
        HttpStatus.BAD_GATEWAY,
        "vlm-error",
        "VLM Service Error",
        e.getMessage());
  }

  @ExceptionHandler(TikaParserException.class)
  public ProblemDetail handleTikaParserException(TikaParserException e) {
    log.error("Tika parser error", e);
    return createProblemDetail(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "tika-parse-failed",
        "Tika Parse Failed",
        e.getMessage());
  }

  @ExceptionHandler(BadRequestException.class)
  public ProblemDetail handleBadRequestException(BadRequestException e) {
    log.warn("Bad request: {}", e.getMessage());
    return createProblemDetail(
        HttpStatus.BAD_REQUEST,
        "bad-request",
        "Bad Request",
        e.getMessage());
  }

  @ExceptionHandler(UnsupportedMediaTypeException.class)
  public ProblemDetail handleUnsupportedMediaTypeException(UnsupportedMediaTypeException e) {
    log.warn("Unsupported media type: {}", e.getMessage());
    return createProblemDetail(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "unsupported-media-type",
        "Unsupported Media Type",
        e.getMessage());
  }

  @ExceptionHandler(ParseKitException.class)
  public ProblemDetail handleParseKitException(ParseKitException e) {
    log.error("ParseKit error", e);
    return createProblemDetail(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "internal-error",
        "Internal Error",
        e.getMessage());
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleException(Exception e) {
    log.error("Unexpected error", e);
    return createProblemDetail(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "internal-error",
        "Internal Error",
        "An unexpected error occurred");
  }
}
