package com.codec.kb.doc;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public final class ParserRegistry {
  private final List<DocumentParser> parsers = new ArrayList<>();

  public ParserRegistry(OcrClient ocr) {
    parsers.add(new TextParser());
    parsers.add(new MarkdownParser());
    parsers.add(new HtmlParser());
    parsers.add(new DocxParser());
    parsers.add(new PdfParser());
    parsers.add(new ExcelParser());
    parsers.add(new ImageOcrParser(ocr));
  }

  public ParsedDocument parseOrNull(Path file, String contentType) throws IOException {
    for (DocumentParser p : parsers) {
      if (p.supports(file, contentType)) {
        return p.parse(file);
      }
    }
    return null;
  }
}
