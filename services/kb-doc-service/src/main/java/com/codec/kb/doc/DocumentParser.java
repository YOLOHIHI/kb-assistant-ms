package com.codec.kb.doc;

import java.io.IOException;
import java.nio.file.Path;

public interface DocumentParser {
  boolean supports(Path file, String contentType);

  ParsedDocument parse(Path file) throws IOException;
}
