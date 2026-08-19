package com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery;

/**
 * Public Request response data for an accepted request.
 *
 * <p>Design references: {@code
 * docs/design/6-username-recovery/username-recovery-api-contract-design.md} section 5 ({@code
 * status}, {@code schemeRef}, {@code maskedDestination}, {@code destinationType}, {@code
 * requestAccepted}).
 *
 * <p>The mapper copies the application-masked destination verbatim and never accepts or remasks a
 * raw destination. {@link #toString()} never discloses the opaque Scheme-Ref or the masked
 * destination value.
 */
public record UsernameRecoveryRequestDataResponse(
    String status,
    String schemeRef,
    String maskedDestination,
    String destinationType,
    boolean requestAccepted) {

  @Override
  public String toString() {
    return "UsernameRecoveryRequestDataResponse[status="
        + status
        + ", schemeRef=<redacted>, maskedDestination=<redacted>"
        + ", destinationType="
        + destinationType
        + ", requestAccepted="
        + requestAccepted
        + "]";
  }
}
