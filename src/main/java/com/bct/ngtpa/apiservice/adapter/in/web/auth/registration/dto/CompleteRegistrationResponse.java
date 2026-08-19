package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto;

import java.util.List;

public record CompleteRegistrationResponse(
    List<RegistrationUnregisteredTrustResponse> unregisteredTrusts,
    List<RegistrationActionResponse> actions) {
  public CompleteRegistrationResponse {
    unregisteredTrusts = unregisteredTrusts == null ? List.of() : List.copyOf(unregisteredTrusts);
    actions = actions == null ? List.of() : List.copyOf(actions);
  }
}
