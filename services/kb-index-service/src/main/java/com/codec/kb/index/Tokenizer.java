package com.codec.kb.index;

import java.util.ArrayList;
import java.util.List;

public final class Tokenizer {
  private Tokenizer() {}

  public static List<String> tokenize(String text) {
    if (text == null || text.isBlank()) return List.of();

    ArrayList<String> out = new ArrayList<>();
    StringBuilder word = new StringBuilder();
    char prevCjk = 0;

    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);

      if (isAsciiWord(c)) {
        word.append(Character.toLowerCase(c));
        prevCjk = 0;
        continue;
      }

      flushWord(word, out);

      if (isCjk(c)) {
        out.add(String.valueOf(c));
        if (prevCjk != 0) out.add("" + prevCjk + c);
        prevCjk = c;
      } else {
        prevCjk = 0;
      }
    }

    flushWord(word, out);
    return out;
  }

  private static void flushWord(StringBuilder word, ArrayList<String> out) {
    if (word.length() == 0) return;
    String w = word.toString();
    if (w.length() >= 2) out.add(w);
    word.setLength(0);
  }

  private static boolean isAsciiWord(char c) {
    return (c >= 'a' && c <= 'z')
        || (c >= 'A' && c <= 'Z')
        || (c >= '0' && c <= '9')
        || c == '_' || c == '-';
  }

  private static boolean isCjk(char c) {
    return (c >= 0x4E00 && c <= 0x9FFF)
        || (c >= 0x3400 && c <= 0x4DBF)
        || (c >= 0xF900 && c <= 0xFAFF);
  }
}
