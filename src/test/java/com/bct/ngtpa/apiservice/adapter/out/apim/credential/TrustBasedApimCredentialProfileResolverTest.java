package com.bct.ngtpa.apiservice.adapter.out.apim.credential;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

class TrustBasedApimCredentialProfileResolverTest {

  @Test
  void trustBasedResolverClassAbsent() {
    try {
      Class.forName(
          "com.bct.ngtpa.apiservice.adapter.out.apim.credential.TrustBasedApimCredentialProfileResolver");
      fail("TrustBasedApimCredentialProfileResolver must be removed from production sources");
    } catch (ClassNotFoundException e) {
      // expected — class removed as part of strict routing migration
    }
  }
}
