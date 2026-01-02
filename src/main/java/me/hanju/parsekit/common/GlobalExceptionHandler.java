package me.hanju.parsekit.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;
import me.hanju.parsekit.converter.exception.JodConverterException;
import me.hanju.parsekit.converter.exception.PopplerConverterException;
import me.hanju.parsekit.parser.exception.DoclingClientException;
import me.hanju.parsekit.parser.exception.VlmClientException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  public record ErrorResponse(String error, String message) {
  }

  @ExceptionHandler(JodConverterException.class)
  public ResponseEntity<ErrorResponse> handleJodConverterException(JodConverterException e) {
    log.error("JodConverter error", e);
    return ResponseEntity
        .status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(new ErrorResponse("CONVERSION_FAILED", e.getMessage()));
  }

  @ExceptionHandler(PopplerConverterException.class)
  public ResponseEntity<ErrorResponse> handlePopplerConverterException(PopplerConverterException e) {
    log.error("Poppler error", e);
    return ResponseEntity
        .status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(new ErrorResponse("IMAGE_CONVERSION_FAILED", e.getMessage()));
  }

  @ExceptionHandler(DoclingClientException.class)
  public ResponseEntity<ErrorResponse> handleDoclingClientException(DoclingClientException e) {
    log.error("Docling client error", e);
    return ResponseEntity
        .status(HttpStatus.BAD_GATEWAY)
        .body(new ErrorResponse("DOCLING_ERROR", e.getMessage()));
  }

  @ExceptionHandler(VlmClientException.class)
  public ResponseEntity<ErrorResponse> handleVlmClientException(VlmClientException e) {
    log.error("VLM client error", e);
    return ResponseEntity
        .status(HttpStatus.BAD_GATEWAY)
        .body(new ErrorResponse("VLM_ERROR", e.getMessage()));
  }

  @ExceptionHandler(ParseKitException.class)
  public ResponseEntity<ErrorResponse> handleParseKitException(ParseKitException e) {
    log.error("ParseKit error", e);
    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ErrorResponse("INTERNAL_ERROR", e.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleException(Exception e) {
    log.error("Unexpected error", e);
    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
  }
}
