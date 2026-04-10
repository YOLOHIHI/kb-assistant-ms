package com.codec.kb.doc;

import java.util.ArrayList;
import java.util.List;

public final class Chunker {
  private final int maxChars;
  private final int overlapChars;

  public Chunker(int maxChars, int overlapChars) {
    this.maxChars = Math.max(200, maxChars);
    this.overlapChars = Math.max(0, Math.min(overlapChars, this.maxChars / 2));
  }

  public List<String> chunk(String text) {
    String t = TextUtil.normalizeWhitespace(text);
    if (t.isEmpty()) return List.of();

    ArrayList<String> out = new ArrayList<>();
    for (String p : TextUtil.splitParagraphs(t)) {
      if (p.length() <= maxChars) out.add(p);
      else out.addAll(slide(p));
    }

    return mergeSmall(out, 120);
  }

  private List<String> slide(String s) {
    ArrayList<String> out = new ArrayList<>();
    int i = 0;
    while (i < s.length()) {
      int end = Math.min(s.length(), i + maxChars);
      String part = s.substring(i, end).trim();
      if (!part.isEmpty()) out.add(part);
      if (end >= s.length()) break;
      i = Math.max(0, end - overlapChars);
      if (i == end) i = end;
    }
    return out;
  }

  private static ArrayList<String> mergeSmall(List<String> in, int minChars) {
    ArrayList<String> out = new ArrayList<>();
    StringBuilder buf = new StringBuilder();
    for (String s : in) {
      if (buf.length() == 0) {
        buf.append(s);
      } else if (buf.length() < minChars) {
        buf.append("\n").append(s);
      } else {
        out.add(buf.toString());
        buf.setLength(0);
        buf.append(s);
      }
    }
    if (buf.length() > 0) out.add(buf.toString());
    return out;
  }
}
