package me.hanju.parsekit.converter.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.jodconverter.core.DocumentConverter;
import org.jodconverter.core.document.DefaultDocumentFormatRegistry;
import org.jodconverter.core.office.OfficeException;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.hanju.parsekit.converter.exception.JodConverterException;

@Slf4j
@Service
@RequiredArgsConstructor
public class JodConverterService {

  private final DocumentConverter documentConverter;

  public byte[] convertToOdt(String filename, byte[] fileBytes) {
    log.info("Converting {} to ODT", filename);
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      documentConverter
          .convert(new ByteArrayInputStream(fileBytes))
          .to(out)
          .as(DefaultDocumentFormatRegistry.ODT)
          .execute();
      log.info("Successfully converted {} to ODT", filename);
      return out.toByteArray();
    } catch (OfficeException e) {
      throw new JodConverterException("convertToOdt failed: " + filename, e);
    }
  }

  public byte[] convertToPdf(String filename, byte[] fileBytes) {
    log.info("Converting {} to PDF", filename);
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      documentConverter
          .convert(new ByteArrayInputStream(fileBytes))
          .to(out)
          .as(DefaultDocumentFormatRegistry.PDF)
          .execute();
      log.info("Successfully converted {} to PDF", filename);
      return out.toByteArray();
    } catch (OfficeException e) {
      throw new JodConverterException("convertToPdf failed: " + filename, e);
    }
  }
}
