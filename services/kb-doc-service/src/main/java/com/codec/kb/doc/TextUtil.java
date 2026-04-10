package com.codec.kb.doc;

import java.util.ArrayList;
import java.util.List;

public final class TextUtil {
  private TextUtil() {}

  public static String normalizeWhitespace(String s) {
    if (s == null) return "";
    if (!s.isEmpty() && s.charAt(0) == '\uFEFF') s = s.substring(1);

    StringBuilder sb = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '\n' || c == '\t' || c >= 0x20) sb.append(c);
    }

    String[] lines = sb.toString().replace("\r", "").split("\n", -1);
    StringBuilder out = new StringBuilder(sb.length());
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i].replaceAll("[ \t]+", " ").trim();
      out.append(line);
      if (i < lines.length - 1) out.append('\n');
    }
    return out.toString().trim();
  }

  public static String stripHtml(String html) {
    if (html == null) return "";
    String s = html;
    s = s.replaceAll("(?is)<script.*?>.*?</script>", " ");
    s = s.replaceAll("(?is)<style.*?>.*?</style>", " ");
    s = s.replaceAll("(?is)<br\\s*/?>", "\n");
    s = s.replaceAll("(?is)</p>", "\n");
    s = s.replaceAll("(?is)<[^>]+>", " ");
    s = s.replace("&nbsp;", " ");
    s = s.replace("&lt;", "<");
    s = s.replace("&gt;", ">");
    s = s.replace("&amp;", "&");
    return normalizeWhitespace(s);
  }

  public static List<String> splitParagraphs(String text) {
    String s = (text == null) ? "" : text.replace("\r", "");
    String[] parts = s.split("\\n{2,}");
    ArrayList<String> out = new ArrayList<>();
    for (String p : parts) {
      String t = p.trim();
      if (!t.isEmpty()) out.add(t);
    }
    return out;
  }
}
