package me.hanju.parsekit.converter.service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.springframework.stereotype.Service;

import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.vladsch.flexmark.util.sequence.Escaping;

import lombok.extern.slf4j.Slf4j;

/**
 * GitHub Flavored Markdown을 HTML로 변환하는 서비스
 */
@Slf4j
@Service
public class MarkdownService {

  private final Parser parser;
  private final HtmlRenderer renderer;

  public MarkdownService() {
    final MutableDataSet options = new MutableDataSet();

    // GitHub Flavored Markdown 확장 활성화
    options.set(Parser.EXTENSIONS, Arrays.asList(
        TablesExtension.create(),
        StrikethroughExtension.create(),
        AutolinkExtension.create(),
        TaskListExtension.create()));

    this.parser = Parser.builder(options).build();
    this.renderer = HtmlRenderer.builder(options).build();
  }

  private static final String HTML_TEMPLATE_WITH_TITLE = """
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="UTF-8">
        <title>%s</title>
        <style>
          body { font-family: sans-serif; line-height: 1.6; max-width: 800px; margin: 0 auto; padding: 20px; }
          table { border-collapse: collapse; width: 100%%; }
          th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
          th { background-color: #f2f2f2; }
          code { background-color: #f4f4f4; padding: 2px 4px; border-radius: 3px; }
          pre { background-color: #f4f4f4; padding: 10px; border-radius: 5px; overflow-x: auto; }
          blockquote { border-left: 4px solid #ddd; margin: 0; padding-left: 16px; color: #666; }
        </style>
      </head>
      <body>
      %s
      </body>
      </html>
      """;

  private static final String HTML_TEMPLATE_WITHOUT_TITLE = """
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="UTF-8">
        <style>
          body { font-family: sans-serif; line-height: 1.6; max-width: 800px; margin: 0 auto; padding: 20px; }
          table { border-collapse: collapse; width: 100%%; }
          th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
          th { background-color: #f2f2f2; }
          code { background-color: #f4f4f4; padding: 2px 4px; border-radius: 3px; }
          pre { background-color: #f4f4f4; padding: 10px; border-radius: 5px; overflow-x: auto; }
          blockquote { border-left: 4px solid #ddd; margin: 0; padding-left: 16px; color: #666; }
        </style>
      </head>
      <body>
      %s
      </body>
      </html>
      """;

  public byte[] convertToFullHtml(final byte[] markdownBytes) {
    final String htmlBody = this.renderMarkdown(markdownBytes);
    final String fullHtml = HTML_TEMPLATE_WITHOUT_TITLE.formatted(htmlBody);
    return fullHtml.getBytes(StandardCharsets.UTF_8);
  }

  public byte[] convertToFullHtml(final byte[] markdownBytes, final String title) {
    if (title == null || title.isBlank()) {
      throw new IllegalStateException("title cannot be null or blank");
    }
    final String htmlBody = this.renderMarkdown(markdownBytes);
    final String fullHtml = HTML_TEMPLATE_WITH_TITLE.formatted(Escaping.escapeHtml(title, false), htmlBody);
    return fullHtml.getBytes(StandardCharsets.UTF_8);
  }

  private String renderMarkdown(final byte[] markdownBytes) {
    if (markdownBytes == null || markdownBytes.length == 0) {
      throw new IllegalStateException("markdownBytes must not be null or empty");
    }
    final String markdown = new String(markdownBytes, StandardCharsets.UTF_8);
    log.debug("Converting markdown to HTML ({} chars)", markdown.length());
    final Node document = parser.parse(markdown);
    return renderer.render(document);
  }
}
