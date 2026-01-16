package me.hanju.parsekit.parser.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import me.hanju.parsekit.common.FileTypeDetector;
import me.hanju.parsekit.common.exception.BadRequestException;
import me.hanju.parsekit.parser.dto.ParseResult;
import me.hanju.parsekit.parser.service.IParserService;

/**
 * 파싱 API 컨트롤러.
 * 설정에 따라 등록된 IParserService 구현체를 사용한다.
 */
@RestController
@RequestMapping("/api/parse")
@RequiredArgsConstructor
public class ParserController {

  private final IParserService parserService;

  @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ParseResult> parse(
      @RequestParam("file") final MultipartFile file,
      @RequestParam(value = "dpi", defaultValue = "150") final int dpi) {
    if (file.isEmpty()) {
      throw new BadRequestException("File is empty");
    }

    final byte[] content = FileTypeDetector.getBytes(file);
    final String filename = file.getOriginalFilename();
    final ParseResult result = parserService.parse(content, filename, dpi);

    return ResponseEntity.ok(result);
  }
}
