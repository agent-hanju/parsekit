package me.hanju.parsekit.parser.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DoclingConvertResponse(
    @JsonProperty("document") DoclingDocument document) {

  public record DoclingDocument(
      @JsonProperty("filename") String filename,
      @JsonProperty("md_content") String mdContent) {
  }
}
