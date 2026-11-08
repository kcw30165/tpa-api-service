package com.bct.ngtpa.apiservice.adapter.out.apim.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.TrpChkCustIdTransportCommand;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpChkCustIdApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import org.junit.jupiter.api.Test;

class ApimIncidentOwnershipTest {
  @Test
  void legacyRequestConstructorsDefaultToObserver() {
    var c = new ApimCredentialResolutionContext(ActorUserRole.ROLE_USER_MBR, null);
    assertThat(
            new ApimExchangeRequest<>(
                    "/x",
                    "r",
                    String.class,
                    String.class,
                    ApimDatabaseScope.NON_ACCOUNT,
                    c,
                    ApimRetryEligibility.SAFE_BUSINESS_CALL)
                .incidentOwnership())
        .isEqualTo(ApimIncidentOwnership.APIM_OBSERVER);
    assertThat(
            new ApimExchangeRequest<>(
                    "/x",
                    "r",
                    String.class,
                    String.class,
                    ApimDatabaseScope.NON_ACCOUNT,
                    c,
                    ApimRetryEligibility.NON_RETRYABLE,
                    ApimResubmissionPolicy.NEVER)
                .incidentOwnership())
        .isEqualTo(ApimIncidentOwnership.APIM_OBSERVER);
  }

  @Test
  void explicitOwnershipPreservedAndNullRejected() {
    var c = new ApimCredentialResolutionContext(ActorUserRole.ROLE_USER_MBR, null);
    assertThat(
            new ApimExchangeRequest<>(
                    "/x",
                    "r",
                    String.class,
                    String.class,
                    ApimDatabaseScope.NON_ACCOUNT,
                    c,
                    ApimRetryEligibility.SAFE_BUSINESS_CALL,
                    ApimResubmissionPolicy.ALLOW_RECOVERY,
                    ApimIncidentOwnership.TERMINAL_CALLER)
                .incidentOwnership())
        .isEqualTo(ApimIncidentOwnership.TERMINAL_CALLER);
    assertThatThrownBy(
            () ->
                new ApimExchangeRequest<>(
                    "/x",
                    "r",
                    String.class,
                    String.class,
                    ApimDatabaseScope.NON_ACCOUNT,
                    c,
                    ApimRetryEligibility.SAFE_BUSINESS_CALL,
                    ApimResubmissionPolicy.ALLOW_RECOVERY,
                    null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("incidentOwnership must not be null");
  }

  @Test
  void canonicalCommandPreservesObserverAndRejectsNull() {
    var req = new TrpChkCustIdApimRequest("u");
    assertThat(
            new TrpChkCustIdTransportCommand(
                    req, ActorUserRole.ROLE_USER_MBR, "r", ApimIncidentOwnership.APIM_OBSERVER)
                .incidentOwnership())
        .isEqualTo(ApimIncidentOwnership.APIM_OBSERVER);
    assertThatThrownBy(
            () -> new TrpChkCustIdTransportCommand(req, ActorUserRole.ROLE_USER_MBR, "r", null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("incidentOwnership must not be null");
  }

  @Test
  void explicitCommandOwnershipIsPreserved() {
    var req = new TrpChkCustIdApimRequest("u");
    assertThat(
            new TrpChkCustIdTransportCommand(
                    req, ActorUserRole.ROLE_USER_MBR, "r", ApimIncidentOwnership.TERMINAL_CALLER)
                .incidentOwnership())
        .isEqualTo(ApimIncidentOwnership.TERMINAL_CALLER);
  }
}
