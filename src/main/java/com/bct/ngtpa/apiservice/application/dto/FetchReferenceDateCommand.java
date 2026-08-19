package com.bct.ngtpa.apiservice.application.dto;

import java.util.Objects;

/** Focused routing command for the internal TRPGetWebSysDate transport. */
public record FetchReferenceDateCommand(String accountEnv, ActorUserRole actorUserRole) {
  public FetchReferenceDateCommand {
    if (accountEnv == null || accountEnv.isBlank())
      throw new IllegalArgumentException("accountEnv must not be blank");
    Objects.requireNonNull(actorUserRole, "actorUserRole must not be null");
  }
}
