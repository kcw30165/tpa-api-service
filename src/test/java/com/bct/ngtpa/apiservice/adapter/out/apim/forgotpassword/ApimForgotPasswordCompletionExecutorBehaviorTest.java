package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCryptoException;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimIncidentOwnership;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimResubmissionPolicy;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.dto.TrpResetPasswordApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.dto.TrpResetPasswordApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.ForgotPasswordCompletionCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.ForgotPasswordCompletionResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.ForgotPasswordCompletionTechnicalFailure;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ApimForgotPasswordCompletionExecutorBehaviorTest {
  private final ApimExchangeExecutor executor = mock(ApimExchangeExecutor.class);
  private final ApimForgotPasswordCompletionAdapter adapter =
      new ApimForgotPasswordCompletionAdapter(executor);

  @Test
  void executorRequestIsExactAndSuccessRemainsReset() {
    stubEnvelope(
        "", List.of(new TrpResetPasswordApimDataItem(true, "db655", "29/07/2026", "14:30:15")));
    StepVerifier.create(adapter.complete(command()))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(ForgotPasswordCompletionResult.Status.RESET);
              assertThat(result.updateDate()).isEqualTo("29/07/2026");
              assertThat(result.updateTime()).isEqualTo("14:30:15");
            })
        .verifyComplete();
    ArgumentCaptor<ApimExchangeRequest> captor = ArgumentCaptor.forClass(ApimExchangeRequest.class);
    verify(executor, times(1)).postEnvelope(captor.capture());
    ApimExchangeRequest<?, ?> request = captor.getValue();
    assertThat(request.path()).isEqualTo("/ws/NGTPA/v1/TRPResetPassword");
    assertThat(request.request()).isEqualTo(new TrpResetPasswordApimRequest("db655", "Password!"));
    assertThat(request.requestType()).isEqualTo(TrpResetPasswordApimRequest.class);
    assertThat(request.responseDataType()).isEqualTo(TrpResetPasswordApimDataItem.class);
    assertThat(request.databaseScope()).isEqualTo(ApimDatabaseScope.NON_ACCOUNT);
    assertThat(request.credentialContext().actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_MBR);
    assertThat(request.credentialContext().accountEnv()).isNull();
    assertThat(request.retryEligibility()).isEqualTo(ApimRetryEligibility.NON_RETRYABLE);
    assertThat(request.resubmissionPolicy()).isEqualTo(ApimResubmissionPolicy.NEVER);
    assertThat(request.incidentOwnership()).isEqualTo(ApimIncidentOwnership.TERMINAL_CALLER);
  }

  @Test
  void recognizedPasswordPolicyRejectionRemainsRejected() {
    stubEnvelope("err.tpa.password.invalid", List.of());
    StepVerifier.create(adapter.complete(command()))
        .assertNext(
            r -> {
              assertThat(r.status()).isEqualTo(ForgotPasswordCompletionResult.Status.REJECTED);
              assertThat(r.downstreamErrorCode()).isEqualTo("err.tpa.password.invalid");
            })
        .verifyComplete();
  }

  @Test
  void recognizedPasswordReuseRejectionRemainsRejected() {
    stubEnvelope("err.tpa.password.used.in.last.5.changes.", List.of());
    StepVerifier.create(adapter.complete(command()))
        .assertNext(
            r -> {
              assertThat(r.status()).isEqualTo(ForgotPasswordCompletionResult.Status.REJECTED);
              assertThat(r.downstreamErrorCode())
                  .isEqualTo("err.tpa.password.used.in.last.5.changes.");
            })
        .verifyComplete();
  }

  @Test
  void recognizedMemberNotFoundRemainsRejected() {
    stubEnvelope("err.tpa.member.not.found", List.of());
    StepVerifier.create(adapter.complete(command()))
        .assertNext(
            r -> {
              assertThat(r.status()).isEqualTo(ForgotPasswordCompletionResult.Status.REJECTED);
              assertThat(r.downstreamErrorCode()).isEqualTo("err.tpa.member.not.found");
            })
        .verifyComplete();
  }

  @Test
  void unrecognizedBusinessErrorRemainsTechnicalFailure() {
    stubEnvelope("private.downstream.detail", List.of());
    StepVerifier.create(adapter.complete(command()))
        .assertNext(
            r -> {
              assertThat(r.status())
                  .isEqualTo(ForgotPasswordCompletionResult.Status.TECHNICAL_FAILURE);
              assertThat(r.downstreamErrorCode()).isNull();
              assertThat(r.failureOrigin()).isEqualTo(FailureOrigin.DOWNSTREAM_CONTRACT);
            })
        .verifyComplete();
  }

  @Test
  void emptyResetDataIsClassifiedAsDownstreamContractFailure() {
    stubEnvelope("", List.of());
    StepVerifier.create(adapter.complete(command()))
        .assertNext(
            result -> {
              assertThat(result.status())
                  .isEqualTo(ForgotPasswordCompletionResult.Status.TECHNICAL_FAILURE);
              assertThat(result.failureOrigin()).isEqualTo(FailureOrigin.DOWNSTREAM_CONTRACT);
            })
        .verifyComplete();
  }

  @Test
  void malformedEnvelopeRemainsOperationSpecificFailure() {
    when(executor.postEnvelope(any()))
        .thenAnswer(i -> Mono.just(new ApimResponseEnvelope<TrpResetPasswordApimDataItem>(null)));
    StepVerifier.create(adapter.complete(command()))
        .expectErrorSatisfies(
            e ->
                assertThat(e)
                    .isInstanceOf(ForgotPasswordCompletionTechnicalFailure.class)
                    .satisfies(
                        failure -> {
                          var technical = (ForgotPasswordCompletionTechnicalFailure) failure;
                          assertThat(technical.origin())
                              .isEqualTo(FailureOrigin.DOWNSTREAM_CONTRACT);
                          assertThat(technical.getCause()).isNull();
                        }))
        .verify();
  }

  @Test
  void unknownExecutorFailureIsClassifiedAsLocalAndCauseFree() {
    when(executor.postEnvelope(any()))
        .thenAnswer(i -> Mono.error(new IllegalStateException("raw downstream secret")));
    StepVerifier.create(adapter.complete(command()))
        .expectErrorSatisfies(
            e ->
                assertThat(e)
                    .isInstanceOf(ForgotPasswordCompletionTechnicalFailure.class)
                    .satisfies(
                        failure -> {
                          var technical = (ForgotPasswordCompletionTechnicalFailure) failure;
                          assertThat(technical.origin()).isEqualTo(FailureOrigin.LOCAL_SYSTEM);
                          assertThat(technical.getCause()).isNull();
                          assertThat(technical.getMessage())
                              .doesNotContain("raw downstream secret");
                        }))
        .verify();
  }

  @Test
  void cryptoFailureIsClassifiedAsInvalidDownstreamResponse() {
    when(executor.postEnvelope(any()))
        .thenAnswer(i -> Mono.error(new ApimCryptoException("ambiguous crypto response")));
    StepVerifier.create(adapter.complete(command()))
        .expectErrorSatisfies(
            failure -> {
              assertThat(failure).isInstanceOf(ForgotPasswordCompletionTechnicalFailure.class);
              assertThat(((ForgotPasswordCompletionTechnicalFailure) failure).origin())
                  .isEqualTo(FailureOrigin.DOWNSTREAM_INVALID_RESPONSE);
            })
        .verify();
  }

  private void stubEnvelope(String error, List<TrpResetPasswordApimDataItem> data) {
    when(executor.postEnvelope(any()))
        .thenAnswer(
            i -> Mono.just(new ApimResponseEnvelope<>(new ApimResponseBody<>(error, data))));
  }

  private ForgotPasswordCompletionCommand command() {
    return new ForgotPasswordCompletionCommand(
        "db655", "Password!", "req-1", ActorUserRole.ROLE_USER_MBR);
  }
}
