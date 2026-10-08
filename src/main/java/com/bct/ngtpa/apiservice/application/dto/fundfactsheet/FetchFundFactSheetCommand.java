package com.bct.ngtpa.apiservice.application.dto.fundfactsheet;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;

/**
 * Input of the fund fact sheet source port. The fields are validated on construction so that an
 * invalid request can never reach the downstream call. Messages never carry a value. The policy
 * number, the certificate number and the scheme type are passed on exactly as given: no trimming
 * and no conversion.
 */
public record FetchFundFactSheetCommand(
    String policyNo,
    String certNo,
    String schemeType,
    String accountEnv,
    ActorUserRole actorUserRole) {

  public FetchFundFactSheetCommand {
    requireText(policyNo, "policyNo");
    requireText(certNo, "certNo");
    requireText(schemeType, "schemeType");
    requireText(accountEnv, "accountEnv");
    if (actorUserRole == null) {
      throw new IllegalArgumentException("actorUserRole must not be null");
    }
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }
}
