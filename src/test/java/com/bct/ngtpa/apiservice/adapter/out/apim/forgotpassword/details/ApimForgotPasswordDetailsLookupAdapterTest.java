package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimIncidentOwnership;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimResubmissionPolicy;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details.dto.ForgotPasswordTrpSearchMemberDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details.dto.ForgotPasswordTrpSearchMemberRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.ForgotPasswordDetailsDownstreamException;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.ForgotPasswordDetailsLookupCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.ForgotPasswordDetailsLookupStatus;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Branch-oriented reactive tests for {@link ApimForgotPasswordDetailsLookupAdapter}. All types
 * verified verbatim (rounds 154-155). {@link ForgotPasswordTrpSearchMemberMapper}'s own complex
 * validation logic already has a dedicated existing test (ForgotPasswordTrpSearchMemberMapperTest,
 * 7 tests) - this test therefore covers only the ADAPTER's own thin orchestration layer
 * (constructor guard, request building/delegation, and the null-envelope/null-response guard),
 * using simple mapper inputs.
 */
class ApimForgotPasswordDetailsLookupAdapterTest {

  private final ApimExchangeExecutor executor = mock(ApimExchangeExecutor.class);

  private ApimForgotPasswordDetailsLookupAdapter newAdapter() {
    return new ApimForgotPasswordDetailsLookupAdapter(executor);
  }

  private ForgotPasswordDetailsLookupCommand command() {
    return new ForgotPasswordDetailsLookupCommand(
        "STAFF_NO",
        "12345",
        "01/01/1990",
        "test@example.com",
        "91234567",
        "cust1",
        "req-1",
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

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Test
  void detailsLookupUsesTerminalCallerWithoutChangingItsRoutingContract() {
    givenExecutorReturns(envelopeWith(null, List.of()));
    StepVerifier.create(newAdapter().verify(command()))
        .assertNext(result -> assertThat(result.status()).isEqualTo(ForgotPasswordDetailsLookupStatus.NOT_FOUND))
        .verifyComplete();

    ArgumentCaptor<ApimExchangeRequest> requestCaptor =
        ArgumentCaptor.forClass(ApimExchangeRequest.class);
    org.mockito.Mockito.verify(executor).postEnvelope(requestCaptor.capture());
    ApimExchangeRequest<?, ?> request = requestCaptor.getValue();
    assertThat(request.path()).isEqualTo(ApimForgotPasswordDetailsLookupAdapter.API_NAME);
    assertThat(request.request())
        .isEqualTo(
            new ForgotPasswordTrpSearchMemberRequest(
                "STAFFNO", "12345", "01/01/1990", "test@example.com", "91234567"));
    assertThat(request.requestType()).isEqualTo(ForgotPasswordTrpSearchMemberRequest.class);
    assertThat(request.responseDataType()).isEqualTo(ForgotPasswordTrpSearchMemberDataItem.class);
    assertThat(request.databaseScope()).isEqualTo(ApimDatabaseScope.NON_ACCOUNT);
    assertThat(request.credentialContext().actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_MBR);
    assertThat(request.credentialContext().accountEnv()).isNull();
    assertThat(request.retryEligibility()).isEqualTo(ApimRetryEligibility.SAFE_BUSINESS_CALL);
    assertThat(request.resubmissionPolicy()).isEqualTo(ApimResubmissionPolicy.ALLOW_RECOVERY);
    assertThat(request.incidentOwnership()).isEqualTo(ApimIncidentOwnership.TERMINAL_CALLER);
  }

  @Test
  void establishedTechnicalFailuresKeepTheirSpecificTerminalOrigins() {
    var adapter = newAdapter();

    assertThat(
            adapter.classifyFailure(
                new ForgotPasswordDetailsDownstreamException(
                    ForgotPasswordDetailsDownstreamException.Reason.UNEXPECTED_ERROR)))
        .isEqualTo(FailureOrigin.DOWNSTREAM_CONTRACT);
    assertThat(
            adapter.classifyFailure(
                new ForgotPasswordDetailsDownstreamException(
                    ForgotPasswordDetailsDownstreamException.Reason.MALFORMED_DATA)))
        .isEqualTo(FailureOrigin.DOWNSTREAM_INVALID_RESPONSE);
    assertThat(
            adapter.classifyFailure(
                new ApimException(
                    HttpStatus.BAD_GATEWAY,
                    ErrorCodes.APIM_RESPONSE_INVALID,
                    "response rejected")))
        .isEqualTo(FailureOrigin.DOWNSTREAM_INVALID_RESPONSE);
    assertThat(
            adapter.classifyFailure(
                new ApimException(
                    HttpStatus.BAD_GATEWAY,
                    ErrorCodes.APIM_UPSTREAM_FAILURE,
                    "upstream rejected")))
        .isEqualTo(FailureOrigin.DOWNSTREAM_TRANSPORT);
    assertThat(
            adapter.isUnavailableFailure(
                new ApimException(
                    HttpStatus.BAD_GATEWAY,
                    ErrorCodes.APIM_UPSTREAM_FAILURE,
                    "upstream rejected")))
        .isFalse();
    assertThat(
            adapter.isUnavailableFailure(
                new ApimException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    ErrorCodes.APIM_SERVICE_UNAVAILABLE,
                    "unavailable")))
        .isTrue();
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
    when(executor.postEnvelope(any()))
        .thenReturn((Mono) Mono.error(new IllegalStateException("boom")));
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
            result ->
                assertThat(result.status()).isEqualTo(ForgotPasswordDetailsLookupStatus.NOT_FOUND))
        .verifyComplete();
  }

  @Test
  void nullDataListIsTreatedAsEmptyByAdapter() {
    givenExecutorReturns(envelopeWith(null, null));
    StepVerifier.create(newAdapter().verify(command()))
        .assertNext(
            result ->
                assertThat(result.status()).isEqualTo(ForgotPasswordDetailsLookupStatus.NOT_FOUND))
        .verifyComplete();
  }

  @Test
  void nullDataItemIsClassifiedAsDownstreamInvalidResponse() {
    var adapter = newAdapter();
    givenExecutorReturns(envelopeWith(null, java.util.Collections.singletonList(null)));

    StepVerifier.create(adapter.verify(command()))
        .expectErrorSatisfies(
            failure -> {
              assertThat(failure)
                  .isInstanceOf(ForgotPasswordDetailsDownstreamException.class);
              assertThat(((ForgotPasswordDetailsDownstreamException) failure).reason())
                  .isEqualTo(ForgotPasswordDetailsDownstreamException.Reason.MALFORMED_DATA);
              assertThat(adapter.classifyFailure(failure))
                  .isEqualTo(FailureOrigin.DOWNSTREAM_INVALID_RESPONSE);
            })
        .verify();
  }

  // ----- happy path: matched member (delegates to mapper's own MATCHED path) -------

  @Test
  void matchedMemberDelegatesToMapperMatchedResult() {
    ForgotPasswordTrpSearchMemberDataItem item =
        new ForgotPasswordTrpSearchMemberDataItem(
            "env1",
            "trust1",
            "SCHEME_A",
            null,
            null,
            "policy1",
            "cert1",
            "ACTIVE",
            null,
            true,
            "cust1");
    givenExecutorReturns(envelopeWith(null, List.of(item)));
    StepVerifier.create(newAdapter().verify(command()))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(ForgotPasswordDetailsLookupStatus.MATCHED);
            })
        .verifyComplete();
  }
}
