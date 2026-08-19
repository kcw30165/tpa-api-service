package com.bct.ngtpa.apiservice.application.dto.session;

import java.util.Objects;
import java.util.regex.Pattern;

public record SessionCookiePolicy(
    String name,
    String path,
    int maxAgeSeconds,
    boolean httpOnly,
    boolean secure,
    SessionCookieSameSite sameSite) {

  private static final Pattern SAFE_COOKIE_NAME = Pattern.compile("^[A-Za-z0-9!#$%&'*+.^_`|~-]+$");

  public SessionCookiePolicy {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(path, "path must not be null");
    Objects.requireNonNull(sameSite, "sameSite must not be null");
    if (name.isBlank() || !SAFE_COOKIE_NAME.matcher(name).matches()) {
      throw new IllegalArgumentException("Session cookie name is invalid");
    }
    if (path.isBlank() || !path.startsWith("/")) {
      throw new IllegalArgumentException("Session cookie path is invalid");
    }
    if (maxAgeSeconds <= 0) {
      throw new IllegalArgumentException("Session cookie maxAgeSeconds must be greater than zero");
    }
    if (sameSite == SessionCookieSameSite.NONE && !secure) {
      throw new IllegalArgumentException("SameSite=None cookies must be Secure");
    }
  }
}
