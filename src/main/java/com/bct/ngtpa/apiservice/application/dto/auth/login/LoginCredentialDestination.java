package com.bct.ngtpa.apiservice.application.dto.auth.login;

import java.util.Objects;

/** Internal protected-context destination. The raw destination is never returned to FE. */
public record LoginCredentialDestination(String channel, String destination) {

  public LoginCredentialDestination {
    Objects.requireNonNull(channel, "channel must not be null");
    Objects.requireNonNull(destination, "destination must not be null");
  }

  @Override
  public String toString() {
    return "LoginCredentialDestination[channel=" + channel + ", destination=<redacted>]";
  }
}
