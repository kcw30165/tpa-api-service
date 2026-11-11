package com.bct.ngtpa.apiservice.application.observability;

/** Strict sanitizer for request IDs written only to log fields. */
public final class RequestIdLogSanitizer {
  private static final int MAX_CODE_POINTS = 128;
  private static final String TRUNCATION_SUFFIX = "...";

  private RequestIdLogSanitizer() {}

  public static String sanitize(String requestId) {
    if (requestId == null || requestId.isBlank()) {
      return null;
    }

    int inputCodePointCount = requestId.codePointCount(0, requestId.length());
    int contentCodePointLimit =
        inputCodePointCount > MAX_CODE_POINTS
            ? MAX_CODE_POINTS - TRUNCATION_SUFFIX.length()
            : MAX_CODE_POINTS;
    StringBuilder sanitized = new StringBuilder(contentCodePointLimit + TRUNCATION_SUFFIX.length());
    int offset = 0;
    for (int index = 0; index < contentCodePointLimit && offset < requestId.length(); index++) {
      int codePoint = requestId.codePointAt(offset);
      if ((codePoint >= 'A' && codePoint <= 'Z')
          || (codePoint >= 'a' && codePoint <= 'z')
          || (codePoint >= '0' && codePoint <= '9')
          || codePoint == '.'
          || codePoint == '_'
          || codePoint == ':'
          || codePoint == '-') {
        sanitized.appendCodePoint(codePoint);
      } else {
        sanitized.append('?');
      }
      offset += Character.charCount(codePoint);
    }
    if (inputCodePointCount > MAX_CODE_POINTS) {
      sanitized.append(TRUNCATION_SUFFIX);
    }
    return sanitized.toString();
  }
}
