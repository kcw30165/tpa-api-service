package com.bct.ngtpa.apiservice.application.usecase.auth.registration.username;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationSelectedTrust;
import java.util.Objects;

/** Registration-owned TRPChkCustId inputs and explicit credential-routing metadata. */
public record RegistrationUsernameAvailabilityCommand(
    RegistrationSelectedTrust selectedTrust,
    String username,
    String correlationId,
    ActorUserRole actorUserRole) {
  public RegistrationUsernameAvailabilityCommand {
    Objects.requireNonNull(selectedTrust, "selectedTrust must not be null");
    Objects.requireNonNull(actorUserRole, "actorUserRole must not be null");
  }

  @Override
  public String toString() {
    return "RegistrationUsernameAvailabilityCommand[selectedTrust="
        + selectedTrust
        + ", username=<redacted>, correlationId="
        + correlationId
        + ", actorUserRole="
        + actorUserRole
        + "]";
  }
}
