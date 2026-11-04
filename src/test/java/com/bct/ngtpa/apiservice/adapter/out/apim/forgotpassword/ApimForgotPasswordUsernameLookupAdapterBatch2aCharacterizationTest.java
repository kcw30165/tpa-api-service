package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.StartForgotPasswordUsernameLookupCommand;
import com.bct.ngtpa.apiservice.exception.ApimException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Batch 2a follow-up: {@link ApimForgotPasswordUsernameLookupAdapter}, stubbing only {@link
 * ApimExchangeExecutor}, mirroring the style of {@code
 * ApimForgotPasswordCompletionAdapterBatch2aCharacterizationTest}.
 *
 * <p>Scenario 1: missing response envelope. The adapter's {@code toTransportEnvelope} throws {@code
 * IllegalStateException("TRPChkCustId response envelope is missing response")} (confirmed at the
 * adapter's real source, lines 79-81).
 *
 * <p>Scenario 2: malformed envelope (no err-message AND no data, or every data item malformed). The
 * adapter throws {@link ApimException}(BAD_GATEWAY, APIM_RESPONSE_INVALID, ...) (confirmed at the
 * adapter's real source, lines 86-100).
 */
class ApimForgotPasswordUsernameLookupAdapterBatch2aCharacterizationTest {

  @Test
  void lookup_missingEnvelope_throwsIllegalStateException() {
    ApimExchangeExecutor executor =
        new ApimExchangeExecutor() {
          @Override
          public <R, D> Mono<ApimResponseEnvelope<D>> postEnvelope(
              ApimExchangeRequest<R, D> request) {
            ApimResponseEnvelope<D> envelope = new ApimResponseEnvelope<>(null);
            return Mono.just(envelope);
          }
        };
    var adapter = new ApimForgotPasswordUsernameLookupAdapter(executor, Map.of());

    var command =
        new StartForgotPasswordUsernameLookupCommand(
            "normalizedUsername-value", "correlationId-value", ActorUserRole.ROLE_INPUT);
    StepVerifier.create(adapter.lookup(command))
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(IllegalStateException.class);
              assertThat(error).hasMessage("TRPChkCustId response envelope is missing response");
            })
        .verify();
  }

  @Test
  void lookup_malformed_throwsApimException() {
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
    var adapter = new ApimForgotPasswordUsernameLookupAdapter(executor, Map.of());

    var command =
        new StartForgotPasswordUsernameLookupCommand(
            "normalizedUsername-value", "correlationId-value", ActorUserRole.ROLE_INPUT);
    StepVerifier.create(adapter.lookup(command))
        .expectErrorSatisfies(
            error ->
                assertThat(error)
                    .isInstanceOfSatisfying(
                        ApimException.class,
                        apimException -> {
                          assertThat(apimException.getStatusCode())
                              .isEqualTo(HttpStatus.BAD_GATEWAY);
                          assertThat(apimException.getErrorCode())
                              .isEqualTo(
                                  com.bct.ngtpa.apiservice.shared.error.ErrorCodes
                                      .APIM_RESPONSE_INVALID);
                        }))
        .verify();
  }
}
