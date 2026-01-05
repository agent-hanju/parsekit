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

  public byte[] convertToOdt(final byte[] fileBytes) {
    try {
      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      documentConverter
          .convert(new ByteArrayInputStream(fileBytes))
          .to(out)
          .as(DefaultDocumentFormatRegistry.ODT)
          .execute();
      return out.toByteArray();
    } catch (final OfficeException e) {
      throw new JodConverterException("convertToOdt failed", e);
    }
  }

  public byte[] convertToPdf(final byte[] fileBytes) {
    try {
      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      documentConverter
          .convert(new ByteArrayInputStream(fileBytes))
          .to(out)
          .as(DefaultDocumentFormatRegistry.PDF)
          .execute();
      return out.toByteArray();
    } catch (final OfficeException e) {
      throw new JodConverterException("convertToPdf failed", e);
    }
  }
}
