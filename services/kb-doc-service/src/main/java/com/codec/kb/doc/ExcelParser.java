package com.codec.kb.doc;

import org.apache.poi.ss.usermodel.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ExcelParser implements DocumentParser {
  @Override
  public boolean supports(Path file, String contentType) {
    String fn = file.getFileName().toString().toLowerCase();
    if (fn.endsWith(".xlsx") || fn.endsWith(".xls")) return true;
    if (contentType == null) return false;
    String ct = contentType.toLowerCase();
    return ct.contains("spreadsheet") || ct.contains("ms-excel");
  }

  @Override
  public ParsedDocument parse(Path file) throws IOException {
    StringBuilder sb = new StringBuilder();

    try (InputStream in = Files.newInputStream(file);
         Workbook wb = WorkbookFactory.create(in)) {

      DataFormatter fmt = new DataFormatter();

      for (int s = 0; s < wb.getNumberOfSheets(); s++) {
        Sheet sheet = wb.getSheetAt(s);
        sb.append("# Sheet: ").append(sheet.getSheetName()).append('\n');

        for (Row row : sheet) {
          int last = row.getLastCellNum();
          if (last < 0) continue;

          for (int c = 0; c < last; c++) {
            Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            String v = (cell == null) ? "" : fmt.formatCellValue(cell);
            sb.append(v);
            if (c != last - 1) sb.append('\t');
          }
          sb.append('\n');
        }
        sb.append('\n');
      }
    } catch (Exception e) {
      throw new IOException("Failed to parse excel", e);
    }

    return new ParsedDocument(file.getFileName().toString(), clean(sb.toString()));
  }

  private static String clean(String s) {
    if (s == null) return "";
    String t = s.replace("\r", "");
    t = t.replaceAll("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]", "");
    return t.trim();
  }
}
