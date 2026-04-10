package com.codec.kb.doc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MarkdownParser implements DocumentParser {
  @Override
  public boolean supports(Path file, String contentType) {
    String n = file.getFileName().toString().toLowerCase();
    return n.endsWith(".md") || n.endsWith(".markdown");
  }

  @Override
  public ParsedDocument parse(Path file) throws IOException {
    String s = Files.readString(file, StandardCharsets.UTF_8);
    return new ParsedDocument(file.getFileName().toString(), TextUtil.normalizeWhitespace(s));
  }
}
