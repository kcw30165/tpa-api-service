package com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.dto;

/**
 * Public scheme projection inside the Start response data.
 *
 * <p>Design references: {@code
 * docs/design/6-username-recovery/username-recovery-api-contract-design.md} section 4 ({@code
 * schemeRef}, {@code accountEnv}, {@code trustCode}, {@code isRegistered}; the Start flow always
 * returns a session-bound opaque Scheme-Ref, including for unregistered schemes).
 *
 * <p>The web mapper copies the opaque {@code schemeRef} string from the application projection; it
 * never fabricates a Scheme-Ref. {@link #toString()} never discloses the opaque Scheme-Ref.
 */
public record UsernameRecoverySchemeResponse(
    String schemeRef, String accountEnv, String trustCode, boolean isRegistered) {

  @Override
  public String toString() {
    return "UsernameRecoverySchemeResponse[schemeRef="
        + (schemeRef == null ? null : "<redacted>")
        + ", accountEnv="
        + accountEnv
        + ", trustCode="
        + trustCode
        + ", isRegistered="
        + isRegistered
        + "]";
  }
}
