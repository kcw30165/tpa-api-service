package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency;

import com.bct.ngtpa.apiservice.application.idempotency.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Username Recovery inputs over the single repository-wide shared idempotency foundation. */
public final class UsernameRecoveryIdempotencyIntegrationPolicy {
  private static final int VERSION = 1;
  private static final IdempotencyCapability CAPABILITY = new IdempotencyCapability("username-recovery");
  private final IdempotencyOperationIdentityFactory identities;

  public UsernameRecoveryIdempotencyIntegrationPolicy(IdempotencyOperationIdentityFactory identities) {
    this.identities = Objects.requireNonNull(identities);
  }

  public IdempotencyOperationId operationId(UsernameRecoveryIdempotencyAction action, String authorizedScope, String clientKey) {
    Objects.requireNonNull(action, "Username Recovery action must not be null");
    return identities.create(CAPABILITY, action.action(), authorizedScope, clientKey);
  }

  public IdempotencyFingerprint start(List<String> establishedCanonicalFields) { return fingerprint(UsernameRecoveryIdempotencyAction.START, establishedCanonicalFields); }
  public IdempotencyFingerprint request(List<String> establishedCanonicalFields) { return fingerprint(UsernameRecoveryIdempotencyAction.REQUEST, establishedCanonicalFields); }
  public IdempotencyFingerprint cancel(List<String> establishedCanonicalFields) { return fingerprint(UsernameRecoveryIdempotencyAction.CANCEL, establishedCanonicalFields); }
  public IdempotencyFailureClassifier provenPreDispatch() { return IdempotencyFailureClassifiers.provenPreDispatch(); }
  public IdempotencyFailureClassifier possibleSideEffect() { return IdempotencyFailureClassifiers.possibleSideEffect(); }

  private static IdempotencyFingerprint fingerprint(UsernameRecoveryIdempotencyAction action, List<String> fields) {
    Objects.requireNonNull(fields, "Username Recovery fingerprint fields must not be null");
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      update(digest, action.legacyCanonicalAction());
      fields.forEach(value -> update(digest, value == null ? "" : value));
      return new IdempotencyFingerprint(VERSION, HexFormat.of().formatHex(digest.digest()));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 digest is unavailable", exception);
    }
  }

  private static void update(MessageDigest digest, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.UTF_8));
    digest.update((byte) 0);
    digest.update(bytes);
    digest.update((byte) 0);
  }
}
