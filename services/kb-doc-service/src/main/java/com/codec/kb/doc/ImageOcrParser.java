package com.codec.kb.doc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ImageOcrParser implements DocumentParser {
  private final OcrClient ocr;

  public ImageOcrParser(OcrClient ocr) {
    this.ocr = ocr;
  }

  @Override
  public boolean supports(Path file, String contentType) {
    if (ocr == null || !ocr.enabled()) return false;

    String fn = file.getFileName().toString().toLowerCase();
    if (fn.endsWith(".png") || fn.endsWith(".jpg") || fn.endsWith(".jpeg") || fn.endsWith(".bmp")
        || fn.endsWith(".tif") || fn.endsWith(".tiff")) {
      return true;
    }

    if (contentType == null) return false;
    return contentType.toLowerCase().startsWith("image/");
  }

  @Override
  public ParsedDocument parse(Path file) throws IOException {
    byte[] bytes = Files.readAllBytes(file);
    String text = ocr.ocr(bytes, file.getFileName().toString(), guessContentType(file));
    if (text == null) text = "";
    return new ParsedDocument(file.getFileName().toString(), text.trim());
  }

  private static String guessContentType(Path file) {
    try {
      String ct = Files.probeContentType(file);
      return ct == null ? "" : ct;
    } catch (IOException e) {
      return "";
    }
  }
}
