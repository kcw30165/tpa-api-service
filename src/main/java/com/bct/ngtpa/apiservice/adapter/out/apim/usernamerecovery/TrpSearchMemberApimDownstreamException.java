package com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery;

import java.util.Objects;

/**
 * Signals a structurally readable TRPSearchMember response that cannot be classified into an
 * application lookup result at the mapper boundary.
 *
 * <p>The raw downstream err-message code is carried only as non-message state so later adapter
 * classification can map it safely; it never appears in the message, stack traces, or logs.
 * Sensitive request/response values are deliberately excluded from this exception.
 */
public final class TrpSearchMemberApimDownstreamException extends RuntimeException {
  private final Reason reason;
  private final String downstreamErrorCode;

  public TrpSearchMemberApimDownstreamException(Reason reason, String downstreamErrorCode) {
    super("TRPSearchMember contract failure: " + reason);
    this.reason = Objects.requireNonNull(reason, "reason must not be null");
    this.downstreamErrorCode =
        downstreamErrorCode == null || downstreamErrorCode.isBlank()
            ? null
            : downstreamErrorCode.trim();
  }

  public Reason reason() {
    return reason;
  }

  public String downstreamErrorCode() {
    return downstreamErrorCode;
  }

  public enum Reason {
    UNEXPECTED_ERROR
  }
}
