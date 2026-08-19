package com.bct.ngtpa.apiservice.adapter.out.apim.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import org.junit.jupiter.api.Test;

class ApimExchangeRequestTest {
  private static final String PATH = "/ws/NGTPA/v1/Test";
  private static final String REQUEST = "request";

  @Test
  void retainsEveryExplicitCallerDecisionWithoutRewritingContext() {
    var context = new ApimCredentialResolutionContext(ActorUserRole.ROLE_USER_MBR, " DEV ");
    var request =
        new ApimExchangeRequest<>(
            PATH,
            REQUEST,
            String.class,
            Integer.class,
            ApimDatabaseScope.NON_ACCOUNT,
            context,
            ApimRetryEligibility.SAFE_BUSINESS_CALL);
    assertThat(request.path()).isEqualTo(PATH);
    assertThat(request.request()).isSameAs(REQUEST);
    assertThat(request.requestType()).isEqualTo(String.class);
    assertThat(request.responseDataType()).isEqualTo(Integer.class);
    assertThat(request.databaseScope()).isEqualTo(ApimDatabaseScope.NON_ACCOUNT);
    assertThat(request.credentialContext()).isSameAs(context);
    assertThat(request.credentialContext().accountEnv()).isEqualTo(" DEV ");
    assertThat(request.retryEligibility()).isEqualTo(ApimRetryEligibility.SAFE_BUSINESS_CALL);
  }

  @Test
  void rejectsNullAndBlankPath() {
    assertThatThrownBy(() -> valid(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("path must not be blank");
    assertThatThrownBy(() -> valid("  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("path must not be blank");
  }

  @Test
  void rejectsNullRequest() {
    assertThatThrownBy(
            () ->
                new ApimExchangeRequest<>(
                    PATH,
                    null,
                    String.class,
                    Integer.class,
                    ApimDatabaseScope.NON_ACCOUNT,
                    context(null),
                    ApimRetryEligibility.NON_RETRYABLE))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("request must not be null");
  }

  @Test
  void rejectsNullOrMismatchedRequestType() {
    assertThatThrownBy(
            () ->
                new ApimExchangeRequest<>(
                    PATH,
                    REQUEST,
                    null,
                    Integer.class,
                    ApimDatabaseScope.NON_ACCOUNT,
                    context(null),
                    ApimRetryEligibility.NON_RETRYABLE))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("requestType must not be null");
    assertThatThrownBy(
            () ->
                new ApimExchangeRequest(
                    PATH,
                    REQUEST,
                    Integer.class,
                    Integer.class,
                    ApimDatabaseScope.NON_ACCOUNT,
                    context(null),
                    ApimRetryEligibility.NON_RETRYABLE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("request must be an instance of requestType");
  }

  @Test
  void rejectsMissingResponseScopeContextRoleAndRetry() {
    assertThatThrownBy(
            () ->
                new ApimExchangeRequest<>(
                    PATH,
                    REQUEST,
                    String.class,
                    null,
                    ApimDatabaseScope.NON_ACCOUNT,
                    context(null),
                    ApimRetryEligibility.NON_RETRYABLE))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new ApimExchangeRequest<>(
                    PATH,
                    REQUEST,
                    String.class,
                    Integer.class,
                    null,
                    context(null),
                    ApimRetryEligibility.NON_RETRYABLE))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new ApimExchangeRequest<>(
                    PATH,
                    REQUEST,
                    String.class,
                    Integer.class,
                    ApimDatabaseScope.NON_ACCOUNT,
                    null,
                    ApimRetryEligibility.NON_RETRYABLE))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new ApimExchangeRequest<>(
                    PATH,
                    REQUEST,
                    String.class,
                    Integer.class,
                    ApimDatabaseScope.NON_ACCOUNT,
                    new ApimCredentialResolutionContext(null, null),
                    ApimRetryEligibility.NON_RETRYABLE))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("credentialContext.actorUserRole must not be null");
    assertThatThrownBy(
            () ->
                new ApimExchangeRequest<>(
                    PATH,
                    REQUEST,
                    String.class,
                    Integer.class,
                    ApimDatabaseScope.NON_ACCOUNT,
                    context(null),
                    null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("retryEligibility must not be null");
  }

  @Test
  void accountEnvScopeRequiresAuthoritativeNonblankEnvironment() {
    assertThatThrownBy(() -> request(ApimDatabaseScope.ACCOUNT_ENV, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ACCOUNT_ENV requires nonblank credentialContext.accountEnv");
    assertThatThrownBy(() -> request(ApimDatabaseScope.ACCOUNT_ENV, " "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(request(ApimDatabaseScope.ACCOUNT_ENV, "DEV").credentialContext().accountEnv())
        .isEqualTo("DEV");
  }

  @Test
  void nonAccountDoesNotRequireOrEraseEnvironment() {
    assertThat(request(ApimDatabaseScope.NON_ACCOUNT, null).credentialContext().accountEnv())
        .isNull();
    assertThat(request(ApimDatabaseScope.NON_ACCOUNT, "DEV").credentialContext().accountEnv())
        .isEqualTo("DEV");
  }

  private ApimExchangeRequest<String, Integer> valid(String path) {
    return new ApimExchangeRequest<>(
        path,
        REQUEST,
        String.class,
        Integer.class,
        ApimDatabaseScope.NON_ACCOUNT,
        context(null),
        ApimRetryEligibility.NON_RETRYABLE);
  }

  private ApimExchangeRequest<String, Integer> request(ApimDatabaseScope scope, String env) {
    return new ApimExchangeRequest<>(
        PATH,
        REQUEST,
        String.class,
        Integer.class,
        scope,
        context(env),
        ApimRetryEligibility.NON_RETRYABLE);
  }

  private ApimCredentialResolutionContext context(String env) {
    return new ApimCredentialResolutionContext(ActorUserRole.ROLE_USER_MBR, env);
  }
}
