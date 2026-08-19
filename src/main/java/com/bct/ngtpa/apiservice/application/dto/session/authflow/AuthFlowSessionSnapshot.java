package com.bct.ngtpa.apiservice.application.dto.session.authflow;

import java.util.Objects;

public record AuthFlowSessionSnapshot(AuthFlowSession session, SessionRevision revision) {
  public AuthFlowSessionSnapshot {
    Objects.requireNonNull(session, "session must not be null");
    Objects.requireNonNull(revision, "revision must not be null");
  }

  @Override
  public String toString() {
    return "AuthFlowSessionSnapshot[session=<redacted>, sessionRevisionPresent=true]";
  }
}
