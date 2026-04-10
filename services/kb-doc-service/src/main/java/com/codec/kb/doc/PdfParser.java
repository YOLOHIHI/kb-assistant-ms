package com.codec.kb.doc;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.file.Path;

public final class PdfParser implements DocumentParser {
  @Override
  public boolean supports(Path file, String contentType) {
    String fn = file.getFileName().toString().toLowerCase();
    if (fn.endsWith(".pdf")) return true;
    return contentType != null && contentType.toLowerCase().contains("application/pdf");
  }

  @Override
  public ParsedDocument parse(Path file) throws IOException {
    try (PDDocument doc = PDDocument.load(file.toFile())) {
      PDFTextStripper stripper = new PDFTextStripper();
      String text = stripper.getText(doc);
      return new ParsedDocument(file.getFileName().toString(), clean(text));
    }
  }

  private static String clean(String s) {
    if (s == null) return "";
    String t = s.replace("\r", "");
    t = t.replaceAll("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]", "");
    return t.trim();
  }
}
