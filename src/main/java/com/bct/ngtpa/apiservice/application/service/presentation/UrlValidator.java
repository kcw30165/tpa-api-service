package com.bct.ngtpa.apiservice.application.service.presentation;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * Validates a URL supplied by a downstream system before it is shown to the user.
 *
 * <p>A value is kept exactly as received when it parses as an absolute URI. {@code null} and blank
 * values are omitted silently. Any other value is omitted and reported through the callback once.
 * The value is never trimmed, and neither the scheme nor the host is restricted.
 *
 * <p>The callback takes no arguments, so a URL can never reach a log through it. Each consumer
 * supplies its own event method. The class is framework-free and is not a Spring bean; consumers
 * construct it inline in their configuration.
 */
public final class UrlValidator {

  private final Runnable onRejected;

  public UrlValidator(Runnable onRejected) {
    this.onRejected = Objects.requireNonNull(onRejected, "onRejected must not be null");
  }

  public Optional<String> validate(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    if (!isAbsoluteUri(value)) {
      onRejected.run();
      return Optional.empty();
    }
    return Optional.of(value);
  }

  private static boolean isAbsoluteUri(String value) {
    try {
      return URI.create(value).isAbsolute();
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }
}
