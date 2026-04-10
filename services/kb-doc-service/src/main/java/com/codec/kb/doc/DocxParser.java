package com.codec.kb.doc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Minimal DOCX text extraction (reads word/document.xml).
 */
public final class DocxParser implements DocumentParser {
  @Override
  public boolean supports(Path file, String contentType) {
    return file.getFileName().toString().toLowerCase().endsWith(".docx");
  }

  @Override
  public ParsedDocument parse(Path file) throws IOException {
    try (ZipFile zf = new ZipFile(file.toFile())) {
      ZipEntry entry = zf.getEntry("word/document.xml");
      if (entry == null) return new ParsedDocument(file.getFileName().toString(), "");

      String xml;
      try (InputStream in = zf.getInputStream(entry)) {
        xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }

      String s = xml;
      s = s.replaceAll("(?is)</w:p>", "\n");
      s = s.replaceAll("(?is)<w:tab\\s*/>", "\t");
      s = s.replaceAll("(?is)<w:br\\s*/>", "\n");
      s = s.replaceAll("(?is)<w:t[^>]*>", "");
      s = s.replaceAll("(?is)</w:t>", "");
      s = s.replaceAll("(?is)<[^>]+>", " ");
      s = s.replace("&lt;", "<").replace("&gt;", ">"
      ).replace("&amp;", "&");

      return new ParsedDocument(file.getFileName().toString(), TextUtil.normalizeWhitespace(s));
    }
  }
}
