package com.bct.ngtpa.apiservice.adapter.out.apim.execution;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import java.util.Objects;

/** Immutable, caller-routed description of one typed APIM envelope exchange. */
public record ApimExchangeRequest<R, D>(
    String path,
    R request,
    Class<R> requestType,
    Class<D> responseDataType,
    ApimDatabaseScope databaseScope,
    ApimCredentialResolutionContext credentialContext,
    ApimRetryEligibility retryEligibility,
    ApimResubmissionPolicy resubmissionPolicy) {

  /** Existing exchanges retain their established token/certificate recovery behavior. */
  public ApimExchangeRequest(
      String path,
      R request,
      Class<R> requestType,
      Class<D> responseDataType,
      ApimDatabaseScope databaseScope,
      ApimCredentialResolutionContext credentialContext,
      ApimRetryEligibility retryEligibility) {
    this(
        path,
        request,
        requestType,
        responseDataType,
        databaseScope,
        credentialContext,
        retryEligibility,
        ApimResubmissionPolicy.ALLOW_RECOVERY);
  }

  public ApimExchangeRequest {
    if (path == null || path.isBlank()) {
      throw new IllegalArgumentException("path must not be blank");
    }
    Objects.requireNonNull(request, "request must not be null");
    Objects.requireNonNull(requestType, "requestType must not be null");
    if (!requestType.isInstance(request)) {
      throw new IllegalArgumentException("request must be an instance of requestType");
    }
    Objects.requireNonNull(responseDataType, "responseDataType must not be null");
    Objects.requireNonNull(databaseScope, "databaseScope must not be null");
    Objects.requireNonNull(credentialContext, "credentialContext must not be null");
    Objects.requireNonNull(
        credentialContext.actorUserRole(), "credentialContext.actorUserRole must not be null");
    Objects.requireNonNull(retryEligibility, "retryEligibility must not be null");
    Objects.requireNonNull(resubmissionPolicy, "resubmissionPolicy must not be null");
    if (resubmissionPolicy == ApimResubmissionPolicy.NEVER
        && retryEligibility != ApimRetryEligibility.NON_RETRYABLE) {
      throw new IllegalArgumentException("NEVER requires NON_RETRYABLE business eligibility");
    }
    if (databaseScope == ApimDatabaseScope.ACCOUNT_ENV
        && (credentialContext.accountEnv() == null || credentialContext.accountEnv().isBlank())) {
      throw new IllegalArgumentException(
          "ACCOUNT_ENV requires nonblank credentialContext.accountEnv");
    }
  }
}
