package com.bct.ngtpa.apiservice.application.dto.auth.csrf;

/** Cookie values are transport inputs and must remain redacted. */
public record RefreshCsrfTokenCommand(
    String authenticatedSessionId,
    String authFlowSessionId,
    String origin,
    String referer,
    String requestId) {
  @Override
  public String toString() {
    return "RefreshCsrfTokenCommand[authenticatedSessionId=<redacted>, authFlowSessionId=<redacted>, origin="
        + origin
        + ", referer="
        + referer
        + ", requestId="
        + requestId
        + "]";
  }
}
