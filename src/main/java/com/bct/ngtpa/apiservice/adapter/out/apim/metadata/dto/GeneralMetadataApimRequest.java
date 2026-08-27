package com.bct.ngtpa.apiservice.adapter.out.apim.metadata.dto;

import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataKey;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataWireValues;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public record GeneralMetadataApimRequest(
    @JsonProperty("env") String accountEnv,
    @JsonProperty("trust-code") String trustCode,
    @JsonProperty("policy-no") String policyNo,
    @JsonProperty("metadata-name") String metadataName,
    @JsonProperty("metadata-type") String metadataType) {
  public GeneralMetadataApimRequest {
    accountEnv = required(accountEnv, "accountEnv");
    trustCode = required(trustCode, "trustCode");
    policyNo = required(policyNo, "policyNo");
    metadataName = Objects.requireNonNull(metadataName, "metadataName must not be null");
    metadataType = Objects.requireNonNull(metadataType, "metadataType must not be null");
  }

  public static GeneralMetadataApimRequest unfiltered(
      String accountEnv, String trustCode, String policyNo) {
    return new GeneralMetadataApimRequest(accountEnv, trustCode, policyNo, "", "");
  }

  public static GeneralMetadataApimRequest targeted(
      String accountEnv, String trustCode, String policyNo, GeneralMetadataKey key) {
    Objects.requireNonNull(key, "key must not be null");
    return new GeneralMetadataApimRequest(
        accountEnv,
        trustCode,
        policyNo,
        GeneralMetadataWireValues.nameToWire(key.name()),
        GeneralMetadataWireValues.typeToWire(key.type()));
  }

  private static String required(String value, String name) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(name + " must not be blank");
    return value.trim();
  }
}
