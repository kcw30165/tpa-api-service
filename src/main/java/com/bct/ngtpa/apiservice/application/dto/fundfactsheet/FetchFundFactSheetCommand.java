package com.bct.ngtpa.apiservice.application.dto.fundfactsheet;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.time.LocalDate;

/**
 * Input of the fund fact sheet source port. The fields are validated on construction so that an
 * invalid request can never reach the downstream call. Messages never carry a value.
 */
public record FetchFundFactSheetCommand(
    String policyNo, LocalDate referenceDate, String accountEnv, ActorUserRole actorUserRole) {

  public FetchFundFactSheetCommand {
    if (policyNo == null || policyNo.isBlank()) {
      throw new IllegalArgumentException("policyNo must not be blank");
    }
    if (referenceDate == null) {
      throw new IllegalArgumentException("referenceDate must not be null");
    }
    if (accountEnv == null || accountEnv.isBlank()) {
      throw new IllegalArgumentException("accountEnv must not be blank");
    }
    if (actorUserRole == null) {
      throw new IllegalArgumentException("actorUserRole must not be null");
    }
  }
}
