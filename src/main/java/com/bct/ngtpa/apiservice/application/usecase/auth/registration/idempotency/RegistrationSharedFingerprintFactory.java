package com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency;

import com.bct.ngtpa.apiservice.application.idempotency.CanonicalIdempotencyFingerprintFactory;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFingerprint;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Compatibility API supplying Registration canonical parts to the central fingerprint factory. */
public final class RegistrationSharedFingerprintFactory {
  public static final int VERSION = 1;
  private final CanonicalIdempotencyFingerprintFactory delegate;

  public RegistrationSharedFingerprintFactory() {
    this(new CanonicalIdempotencyFingerprintFactory());
  }

  RegistrationSharedFingerprintFactory(CanonicalIdempotencyFingerprintFactory delegate) {
    this.delegate = Objects.requireNonNull(delegate);
  }

  public IdempotencyFingerprint create(
      RegistrationSharedIdempotencyAction action, List<String> normalizedParts) {
    Objects.requireNonNull(action, "registration idempotency action must not be null");
    var parts = new ArrayList<String>();
    parts.add(action.value());
    parts.addAll(normalizedParts == null ? List.of() : normalizedParts);
    return delegate.createFromCanonicalParts(VERSION, parts);
  }
}
