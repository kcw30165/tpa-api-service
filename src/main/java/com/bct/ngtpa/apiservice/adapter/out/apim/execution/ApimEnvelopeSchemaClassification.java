package com.bct.ngtpa.apiservice.adapter.out.apim.execution;

import java.util.Objects;

/**
 * Pure classification result for an APIM response envelope's schema shape.
 *
 * <p>This classification is used only to decide what {@link ApimExchangeOutcomeLogger} should
 * log. It never throws on its own and never influences the envelope or exception that flows back
 * to the calling adapter.
 */
public record ApimEnvelopeSchemaClassification(Kind kind, String malformedReason) {

  public ApimEnvelopeSchemaClassification {
    Objects.requireNonNull(kind, "kind must not be null");
    if (kind == Kind.MALFORMED) {
      Objects.requireNonNull(
          malformedReason, "malformedReason must not be null when kind is MALFORMED");
    }
  }

  public static ApimEnvelopeSchemaClassification malformed(String reason) {
    return new ApimEnvelopeSchemaClassification(Kind.MALFORMED, reason);
  }

  public static ApimEnvelopeSchemaClassification businessRejected() {
    return new ApimEnvelopeSchemaClassification(Kind.BUSINESS_REJECTED, null);
  }

  public static ApimEnvelopeSchemaClassification success() {
    return new ApimEnvelopeSchemaClassification(Kind.SUCCESS, null);
  }

  /** The three possible schema outcomes for a decrypted APIM response envelope. */
  public enum Kind {
    /** {@code response}, {@code err-message}, or {@code data} (when err-message is empty) is missing. */
    MALFORMED,
    /** Well-formed envelope with a non-empty err-message (a recognized or unrecognized business rejection). */
    BUSINESS_REJECTED,
    /** Well-formed envelope with an empty err-message and a present data array (possibly empty). */
    SUCCESS
  }
}
