package com.bct.ngtpa.apiservice.application.idempotency;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Allowlist for non-sensitive response headers persisted in a terminal snapshot. */
public final class IdempotencySafeHeaders {
  private static final Map<String, String> ALLOWED =
      Map.of(
          "content-language", "Content-Language",
          "retry-after", "Retry-After",
          "location", "Location");

  private IdempotencySafeHeaders() {}

  public static Map<String, String> copyOf(Map<String, String> headers) {
    if (headers == null || headers.isEmpty()) {
      return Map.of();
    }
    Map<String, String> result = new LinkedHashMap<>();
    headers.forEach(
        (name, value) -> {
          if (name == null || value == null || value.isBlank()) {
            throw new IllegalArgumentException("idempotent response header is invalid");
          }
          String canonical = ALLOWED.get(name.trim().toLowerCase(Locale.ROOT));
          if (canonical == null) {
            throw new IllegalArgumentException("idempotent response header is not allowlisted");
          }
          result.put(canonical, value.trim());
        });
    return Map.copyOf(result);
  }
}
