package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoverySchemeProjection;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult.Outcome;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord.ReplaySnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Secure START replay snapshot codec for the two non-session outcomes (Task 019).
 *
 * <p>Design references: {@code docs/design/6-username-recovery/handover.md} line 386 (replay
 * snapshots must never contain username, raw destination, identity, DOB, CSRF, cookie, Scheme-Ref
 * secrets, or raw exception data). {@code SESSION_CREATED} is deliberately rejected here: it
 * belongs exclusively to the committed 019B {@link UsernameRecoveryStartReplayCodec}, which
 * persists only the protected session id and rotates CSRF on replay. This codec stores only the
 * outcome token plus the non-secret length-prefixed accountEnv/trustCode pairs of the
 * zero-registered projections.
 *
 * <p>Corrupt or unrelated snapshots decode as definite {@code MALFORMED} (never an exception, never
 * a blind second session).
 */
public final class UsernameRecoveryStartReplaySnapshot {

  private static final String PREFIX = "ur-start-non-session:v1:";
  private static final String MEMBER_NOT_FOUND_TOKEN = "member-not-found";
  private static final String ZERO_REGISTERED_TOKEN = "zero-registered";

  private UsernameRecoveryStartReplaySnapshot() {}

  /**
   * Encodes a {@code MEMBER_NOT_FOUND} or {@code ZERO_REGISTERED} start result into the opaque
   * replay snapshot. {@code SESSION_CREATED} is rejected: use the committed {@link
   * UsernameRecoveryStartReplayCodec}.
   */
  public static ReplaySnapshot encode(UsernameRecoveryStartResult result) {
    Objects.requireNonNull(result, "result must not be null");
    return switch (result.outcome()) {
      case SESSION_CREATED ->
          throw new IllegalArgumentException(
              "session-created replay must use UsernameRecoveryStartReplayCodec");
      case MEMBER_NOT_FOUND -> new ReplaySnapshot(PREFIX + MEMBER_NOT_FOUND_TOKEN);
      case ZERO_REGISTERED -> new ReplaySnapshot(encodeZeroRegistered(result.schemes()));
    };
  }

  /**
   * Decodes a non-session replay snapshot. A {@code DECODED} result carries the reconstructed safe
   * result; {@code MALFORMED} carries none and is a definite-corruption signal.
   */
  public static DecodeResult decode(ReplaySnapshot snapshot) {
    if (snapshot == null) {
      return DecodeResult.malformed();
    }
    String value = snapshot.value();
    if (!value.startsWith(PREFIX)) {
      return DecodeResult.malformed();
    }
    String body = value.substring(PREFIX.length());
    if (body.equals(MEMBER_NOT_FOUND_TOKEN)) {
      return DecodeResult.decoded(
          new UsernameRecoveryStartResult(Outcome.MEMBER_NOT_FOUND, null, null, List.of()));
    }
    if (!body.startsWith(ZERO_REGISTERED_TOKEN + ":")) {
      return DecodeResult.malformed();
    }
    return decodeZeroRegistered(body.substring(ZERO_REGISTERED_TOKEN.length() + 1));
  }

  private static String encodeZeroRegistered(List<UsernameRecoverySchemeProjection> schemes) {
    StringBuilder builder = new StringBuilder(PREFIX).append(ZERO_REGISTERED_TOKEN).append(':');
    for (UsernameRecoverySchemeProjection projection : schemes) {
      builder.append(encodePart(projection.accountEnv()));
      builder.append(encodePart(projection.trustCode()));
    }
    return builder.toString();
  }

  private static String encodePart(String value) {
    return value.length() + ":" + value;
  }

  private static DecodeResult decodeZeroRegistered(String encoded) {
    List<UsernameRecoverySchemeProjection> projections = new ArrayList<>();
    int index = 0;
    try {
      while (index < encoded.length()) {
        int colon = encoded.indexOf(':', index);
        if (colon < 0) {
          return DecodeResult.malformed();
        }
        int accountEnvLength = Integer.parseInt(encoded.substring(index, colon));
        if (accountEnvLength < 0 || colon + 1 + accountEnvLength > encoded.length()) {
          return DecodeResult.malformed();
        }
        String accountEnv = encoded.substring(colon + 1, colon + 1 + accountEnvLength);
        index = colon + 1 + accountEnvLength;

        colon = encoded.indexOf(':', index);
        if (colon < 0) {
          return DecodeResult.malformed();
        }
        int trustCodeLength = Integer.parseInt(encoded.substring(index, colon));
        if (trustCodeLength < 0 || colon + 1 + trustCodeLength > encoded.length()) {
          return DecodeResult.malformed();
        }
        String trustCode = encoded.substring(colon + 1, colon + 1 + trustCodeLength);
        index = colon + 1 + trustCodeLength;

        if (accountEnv.isBlank() || trustCode.isBlank()) {
          return DecodeResult.malformed();
        }
        projections.add(new UsernameRecoverySchemeProjection(null, accountEnv, trustCode, false));
      }
    } catch (NumberFormatException | IndexOutOfBoundsException exception) {
      return DecodeResult.malformed();
    }
    if (projections.isEmpty()) {
      return DecodeResult.malformed();
    }
    return DecodeResult.decoded(
        new UsernameRecoveryStartResult(Outcome.ZERO_REGISTERED, null, null, projections));
  }

  /**
   * Typed decode outcome: {@code DECODED} carries the reconstructed result; {@code MALFORMED} none.
   */
  public record DecodeResult(Status status, UsernameRecoveryStartResult result) {

    public enum Status {
      DECODED,
      MALFORMED
    }

    public DecodeResult {
      Objects.requireNonNull(status, "status must not be null");
      boolean decoded = status == Status.DECODED;
      if (decoded && result == null) {
        throw new IllegalArgumentException("decoded result requires a result");
      }
      if (!decoded && result != null) {
        throw new IllegalArgumentException("malformed result must not carry a result");
      }
    }

    public static DecodeResult decoded(UsernameRecoveryStartResult result) {
      return new DecodeResult(Status.DECODED, result);
    }

    public static DecodeResult malformed() {
      return new DecodeResult(Status.MALFORMED, null);
    }

    public boolean decoded() {
      return status == Status.DECODED;
    }

    @Override
    public String toString() {
      return "DecodeResult[status=" + status + ", result=" + result + "]";
    }
  }
}
