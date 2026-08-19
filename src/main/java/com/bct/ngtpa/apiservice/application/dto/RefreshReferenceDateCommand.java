package com.bct.ngtpa.apiservice.application.dto;

import java.util.Objects;

public record RefreshReferenceDateCommand(String accountEnv, ActorUserRole actorUserRole) {
  public RefreshReferenceDateCommand {
    if (accountEnv == null || accountEnv.isBlank())
      throw new IllegalArgumentException("accountEnv must not be blank");
    Objects.requireNonNull(actorUserRole, "actorUserRole must not be null");
  }
}
