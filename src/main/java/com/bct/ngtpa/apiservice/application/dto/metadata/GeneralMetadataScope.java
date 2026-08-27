package com.bct.ngtpa.apiservice.application.dto.metadata;

public record GeneralMetadataScope(String accountEnv, String trustCode, String policyNo) {
  public GeneralMetadataScope {
    accountEnv = require(accountEnv, "accountEnv");
    trustCode = require(trustCode, "trustCode");
    policyNo = require(policyNo, "policyNo");
  }

  private static String require(String value, String name) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(name + " must not be blank");
    return value.trim();
  }
}
