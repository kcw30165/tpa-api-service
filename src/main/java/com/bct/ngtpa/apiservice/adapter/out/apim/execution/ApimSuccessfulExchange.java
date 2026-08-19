package com.bct.ngtpa.apiservice.adapter.out.apim.execution;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialProfile;
import java.security.PublicKey;
import java.util.Objects;

/** Raw metadata from the successful HTTP attempt, before envelope or business interpretation. */
public record ApimSuccessfulExchange(
    String responseBody, ApimCredentialProfile profile, PublicKey responseVerificationKey) {

  public ApimSuccessfulExchange {
    Objects.requireNonNull(responseBody, "responseBody must not be null");
    Objects.requireNonNull(profile, "profile must not be null");
  }
}
