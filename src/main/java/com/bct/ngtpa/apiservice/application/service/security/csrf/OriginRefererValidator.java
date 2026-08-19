package com.bct.ngtpa.apiservice.application.service.security.csrf;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class OriginRefererValidator {

  public OriginRefererValidationResult validate(
      String origin, String referer, Collection<String> allowedOrigins) {
    Set<OriginValue> allowed = normalizeAllowedOrigins(allowedOrigins);
    if (allowed.isEmpty()) {
      return OriginRefererValidationResult.failure(
          OriginRefererValidationStatus.ORIGIN_NOT_ALLOWED);
    }

    if (hasText(origin)) {
      OriginValue parsedOrigin = parseOrigin(origin, false);
      if (parsedOrigin == null) {
        return OriginRefererValidationResult.failure(OriginRefererValidationStatus.MALFORMED);
      }
      return allowed.contains(parsedOrigin)
          ? OriginRefererValidationResult.success()
          : OriginRefererValidationResult.failure(OriginRefererValidationStatus.ORIGIN_NOT_ALLOWED);
    }

    if (hasText(referer)) {
      OriginValue parsedReferer = parseOrigin(referer, true);
      if (parsedReferer == null) {
        return OriginRefererValidationResult.failure(OriginRefererValidationStatus.MALFORMED);
      }
      return allowed.contains(parsedReferer)
          ? OriginRefererValidationResult.success()
          : OriginRefererValidationResult.failure(
              OriginRefererValidationStatus.REFERER_NOT_ALLOWED);
    }

    return OriginRefererValidationResult.failure(OriginRefererValidationStatus.MISSING);
  }

  private Set<OriginValue> normalizeAllowedOrigins(Collection<String> allowedOrigins) {
    if (allowedOrigins == null || allowedOrigins.isEmpty()) {
      return Set.of();
    }
    return allowedOrigins.stream()
        .filter(this::hasText)
        .map(String::trim)
        .filter(value -> !"*".equals(value))
        .map(value -> parseOrigin(value, false))
        .filter(Objects::nonNull)
        .collect(Collectors.toUnmodifiableSet());
  }

  private OriginValue parseOrigin(String value, boolean allowPathQueryFragment) {
    try {
      URI uri = new URI(value.trim());
      if (!hasText(uri.getScheme()) || !hasText(uri.getHost())) {
        return null;
      }
      if (!allowPathQueryFragment && hasPathQueryOrFragment(uri)) {
        return null;
      }
      return new OriginValue(
          uri.getScheme().toLowerCase(Locale.ROOT),
          uri.getHost().toLowerCase(Locale.ROOT),
          uri.getPort());
    } catch (IllegalArgumentException | URISyntaxException exception) {
      return null;
    }
  }

  private boolean hasPathQueryOrFragment(URI uri) {
    return hasText(uri.getRawPath()) || hasText(uri.getRawQuery()) || hasText(uri.getRawFragment());
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private record OriginValue(String scheme, String host, int port) {}
}
