package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.ForgotPasswordCompletionCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.ForgotPasswordCompletionResult;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Sub-task 2b.2 STEP 1, Batch 2a (Forgot Password Completion) Test A: real {@link
 * ApimForgotPasswordCompletionAdapter}, stubbing only {@link ApimExchangeExecutor}. Per Nick's
 * 2026-10-27 "joined by two tests" decision, this test characterizes the adapter's result/
 * exception in isolation; Test B separately characterizes the
 * IdempotentCompleteForgotPasswordService-to-response path with a stubbed protected operation.
 * The adapter-to-ForgotPasswordCompleteProtectedOperation seam (classify/blockUnknown at
 * ForgotPasswordCompleteProtectedOperation.java:135,:191-197) is explicitly NOT covered end to
 * end by either test; see the report for that gap.
 *
 * <p>This class intentionally asserts no log output. The adapter itself emits no log line of its
 * own (no {@code @Slf4j}/log field); {@code method.execution.*} lines are pinned by {@link
 * ApimForgotPasswordCompletionAdapterLogExecutionAspectCharacterizationTest} (aspect-weaving),
 * and executor-layer lines ({@code apim.exchange.failed}, {@code apim.contract.invalid}) are
 * pinned by {@code DefaultApimExchangeExecutorLoggingTest}.
 *
 * <p>{@code ForgotPasswordCompletionCommand}'s 4-arg constructor used below compiles against the
 * real class.
 */
class ApimForgotPasswordCompletionAdapterBatch2aCharacterizationTest {

  /**
   * Scenario 1 of 2: the downstream envelope itself is missing (envelope.getResponse() is null).
   * {@code toEnvelope()} throws {@link ApimException} synchronously rather than returning a
   * result value.
   */
  @Test
  void complete_missingEnvelope_throwsApimException() {
    ApimExchangeExecutor executor =
        new ApimExchangeExecutor() {
          @Override
          public <R, D> Mono<ApimResponseEnvelope<D>> postEnvelope(
              ApimExchangeRequest<R, D> request) {
            ApimResponseEnvelope<D> envelope = new ApimResponseEnvelope<>(null);
            return Mono.just(envelope);
          }
        };
    var adapter = new ApimForgotPasswordCompletionAdapter(executor);
    var command =
        new ForgotPasswordCompletionCommand(
            "afs-batch2a-completion-missing-envelope", "member1", "Password1!", ActorUserRole.ROLE_USER_MBR);

    StepVerifier.create(adapter.complete(command))
        .expectErrorSatisfies(
            error ->
                assertThat(error)
                    .isInstanceOfSatisfying(
                        ApimException.class,
                        apimException -> {
                          assertThat(apimException.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                          assertThat(apimException.getErrorCode()).isEqualTo(ErrorCodes.APIM_RESPONSE_INVALID);
                        }))
        .verify();
  }

  /**
   * Scenario 2 of 2: the downstream envelope is present with an empty {@code err-message} and
   * empty {@code data}. {@code toEnvelope()} does not throw (envelope.getResponse() is
   * non-null); {@code resultMapper.toResult(...)} maps this to a normal {@link
   * ForgotPasswordCompletionResult} with status {@code TECHNICAL_FAILURE}, not an exception.
   */
  @Test
  void complete_emptyButPresentResponse_returnsTechnicalFailureResult() {
    ApimExchangeExecutor executor =
        new ApimExchangeExecutor() {
          @Override
          public <R, D> Mono<ApimResponseEnvelope<D>> postEnvelope(
              ApimExchangeRequest<R, D> request) {
            ApimResponseBody<D> body = new ApimResponseBody<>("", List.of());
            ApimResponseEnvelope<D> envelope = new ApimResponseEnvelope<>(body);
            return Mono.just(envelope);
          }
        };
    var adapter = new ApimForgotPasswordCompletionAdapter(executor);
    var command =
        new ForgotPasswordCompletionCommand(
            "afs-batch2a-completion-empty-but-present", "member1", "Password1!", ActorUserRole.ROLE_USER_MBR);

    ForgotPasswordCompletionResult result = adapter.complete(command).block();

    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(ForgotPasswordCompletionResult.Status.TECHNICAL_FAILURE);
  }
}
