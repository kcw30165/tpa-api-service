package com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery;

import java.util.Locale;

/**
 * Turns the {@code Accept-Language} header of a Username Recovery {@code /request} call into the
 * language its notice is rendered in. A call that names no usable language gives {@code null}, and
 * the language that START stored in the session is then used.
 *
 * <p>Like START, this reads a single language tag. A tag without a language, such as {@code *} or
 * text that is not a tag, names none.
 */
final class UsernameRecoveryRequestLanguageResolver {

  private UsernameRecoveryRequestLanguageResolver() {}

  static Locale fromHeader(String header) {
    if (header == null || header.isBlank()) {
      return null;
    }
    Locale locale = Locale.forLanguageTag(header.trim());
    return locale.getLanguage().isEmpty() ? null : locale;
  }
}
