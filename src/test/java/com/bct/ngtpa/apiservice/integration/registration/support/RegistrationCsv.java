package com.bct.ngtpa.apiservice.integration.registration.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class RegistrationCsv {

  private RegistrationCsv() {}

  static List<List<String>> read(Path path) throws IOException {
    String text = Files.readString(path, StandardCharsets.UTF_8);
    if (!text.isEmpty() && text.charAt(0) == '\ufeff') {
      text = text.substring(1);
    }
    List<List<String>> rows = new ArrayList<>();
    List<String> row = new ArrayList<>();
    StringBuilder field = new StringBuilder();
    boolean quoted = false;

    for (int index = 0; index < text.length(); index++) {
      char current = text.charAt(index);
      if (quoted) {
        if (current == '"' && index + 1 < text.length() && text.charAt(index + 1) == '"') {
          field.append('"');
          index++;
        } else if (current == '"') {
          quoted = false;
        } else {
          field.append(current);
        }
      } else if (current == '"') {
        quoted = true;
      } else if (current == ',') {
        row.add(field.toString());
        field.setLength(0);
      } else if (current == '\n') {
        row.add(trimCarriageReturn(field.toString()));
        field.setLength(0);
        rows.add(List.copyOf(row));
        row.clear();
      } else {
        field.append(current);
      }
    }
    if (quoted) {
      throw new IOException("Unterminated quoted CSV field in " + path);
    }
    if (!row.isEmpty() || !field.isEmpty()) {
      row.add(trimCarriageReturn(field.toString()));
      rows.add(List.copyOf(row));
    }
    return rows;
  }

  static String encode(List<String> columns) {
    return columns.stream()
        .map(RegistrationCsv::escape)
        .reduce((left, right) -> left + "," + right)
        .orElse("");
  }

  private static String escape(String value) {
    String safe = value == null ? "" : value;
    if (safe.indexOf(',') >= 0
        || safe.indexOf('"') >= 0
        || safe.indexOf('\n') >= 0
        || safe.indexOf('\r') >= 0) {
      return '"' + safe.replace("\"", "\"\"") + '"';
    }
    return safe;
  }

  private static String trimCarriageReturn(String value) {
    return value.endsWith("\r") ? value.substring(0, value.length() - 1) : value;
  }
}
