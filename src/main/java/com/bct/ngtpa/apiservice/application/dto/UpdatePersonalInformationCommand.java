package com.bct.ngtpa.apiservice.application.dto;

import java.util.LinkedHashMap;
import java.util.Map;

public record UpdatePersonalInformationCommand(
    Boolean applyToAllAccounts,
    Map<String, Object> updateFields,
    String accountEnv,
    String policyNo,
    String certNo,
    String actorUserId,
    ActorUserRole actorUserRole) {
  public UpdatePersonalInformationCommand {
    updateFields = updateFields == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(updateFields));
  }

  /**
   * Inbound mapper compatibility constructor. The mapper owns request-field mapping only; the
   * authenticated controller must replace this partial command with a fully trusted command before
   * invoking the use case.
   */
  public UpdatePersonalInformationCommand(
      Boolean applyToAllAccounts, Map<String, Object> updateFields) {
    this(applyToAllAccounts, updateFields, null, null, null, null, null);
  }
}
