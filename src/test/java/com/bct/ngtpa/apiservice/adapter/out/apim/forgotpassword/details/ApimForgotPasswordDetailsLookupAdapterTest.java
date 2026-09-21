package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details.dto.ForgotPasswordTrpSearchMemberDataItem;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.ForgotPasswordDetailsDownstreamException;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.ForgotPasswordDetailsLookupCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.ForgotPasswordDetailsLookupResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.ForgotPasswordDetailsLookupStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Branch-oriented reactive tests for {@link ApimForgotPasswordDetailsLookupAdapter}. All types
 * verified verbatim (rounds 154-155). {@link ForgotPasswordTrpSearchMemberMapper}'s own complex
 * validation logic already has a dedicated existing test
 * (ForgotPasswordTrpSearchMemberMapperTest, 7 tests) - this test therefore covers only the
 * ADAPTER's own thin orchestration layer (constructor guard, request building/delegation, and the
 * null-envelope/null-response guard), using simple mapper inputs.
 */
class ApimForgotPasswordDetailsLookupAdapterTest {

  private final ApimExchangeExecutor executor = mock(ApimExchangeExecutor.class);

  private ApimForgotPasswordDetailsLookupAdapter newAdapter() {
    return new ApimForgotPasswordDetailsLookupAdapter(executor);
  }

  private ForgotPasswordDetailsLookupCommand command() {
    return new ForgotPasswordDetailsLookupCommand(
        "STAFF_NO", "12345", "01/01/1990", "test@example.com", "91234567", "cust1", "req-1",
        ActorUserRole.ROLE_USER_MBR);
  }

  // ----- constructor guard --------------------------------------------------

  @Test
  void constructorRejectsNullExecutor() {
    assertThatThrownBy(() -> new ApimForgotPasswordDetailsLookupAdapter(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void verifyRejectsNullCommand() {
    assertThatThrownBy(() -> newAdapter().verify(null)).isInstanceOf(NullPointerException.class);
  }

  // ----- null envelope / null response guards -------------------------------------

  @SuppressWarnings("unchecked")
  @Test
  void nullEnvelopeYieldsUnexpectedErrorException() {
    when(executor.postEnvelope(any())).thenReturn((Mono) Mono.just(new ApimResponseEnvelope<>()));
    StepVerifier.create(newAdapter().verify(command()))
        .expectErrorSatisfies(
            ex -> {
              assertThat(ex).isInstanceOf(ForgotPasswordDetailsDownstreamException.class);
              assertThat(((ForgotPasswordDetailsDownstreamException) ex).reason())
                  .isEqualTo(ForgotPasswordDetailsDownstreamException.Reason.UNEXPECTED_ERROR);
            })
        .verify();
  }

  @SuppressWarnings("unchecked")
  private ApimResponseEnvelope<ForgotPasswordTrpSearchMemberDataItem> envelopeWith(
      String errMessage, List<ForgotPasswordTrpSearchMemberDataItem> data) {
    return ApimResponseEnvelope.<ForgotPasswordTrpSearchMemberDataItem>builder()
        .response(
            ApimResponseBody.<ForgotPasswordTrpSearchMemberDataItem>builder()
                .errMessage(errMessage)
                .data(data)
                .build())
        .build();
  }

  @SuppressWarnings("unchecked")
  private void givenExecutorReturns(
      ApimResponseEnvelope<ForgotPasswordTrpSearchMemberDataItem> envelope) {
    when(executor.postEnvelope(any())).thenReturn((Mono) Mono.just(envelope));
  }

  // ----- transport / executor error propagation ---------------------------------

  @SuppressWarnings("unchecked")
  @Test
  void executorErrorPropagates() {
    when(executor.postEnvelope(any())).thenReturn((Mono) Mono.error(new IllegalStateException("boom")));
    StepVerifier.create(newAdapter().verify(command()))
        .expectError(IllegalStateException.class)
        .verify();
  }

  // ----- happy path: empty data (delegates to mapper's own NOT_FOUND) ---------------

  @Test
  void emptyDataDelegatesToMapperNotFound() {
    givenExecutorReturns(envelopeWith(null, List.of()));
    StepVerifier.create(newAdapter().verify(command()))
        .assertNext(
            result -> assertThat(result.status()).isEqualTo(ForgotPasswordDetailsLookupStatus.NOT_FOUND))
        .verifyComplete();
  }

  @Test
  void nullDataListIsTreatedAsEmptyByAdapter() {
    givenExecutorReturns(envelopeWith(null, null));
    StepVerifier.create(newAdapter().verify(command()))
        .assertNext(
            result -> assertThat(result.status()).isEqualTo(ForgotPasswordDetailsLookupStatus.NOT_FOUND))
        .verifyComplete();
  }

  // ----- happy path: matched member (delegates to mapper's own MATCHED path) -------

  @Test
  void matchedMemberDelegatesToMapperMatchedResult() {
    ForgotPasswordTrpSearchMemberDataItem item =
        new ForgotPasswordTrpSearchMemberDataItem(
            "env1", "trust1", "SCHEME_A", "policy1", "cert1", "ACTIVE", null, true, "cust1");
    givenExecutorReturns(envelopeWith(null, List.of(item)));
    StepVerifier.create(newAdapter().verify(command()))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(ForgotPasswordDetailsLookupStatus.MATCHED);
              assertThat(result.accountEnv()).isEqualTo("env1");
            })
        .verifyComplete();
  }
}
