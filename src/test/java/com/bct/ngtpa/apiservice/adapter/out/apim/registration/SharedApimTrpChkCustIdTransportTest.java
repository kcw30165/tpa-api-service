package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpChkCustIdApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpChkCustIdApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SharedApimTrpChkCustIdTransportTest {

  @Test
  void delegatesTypedNonAccountExchangeWithCallerSuppliedRole() {
    ApimExchangeExecutor executor = mock(ApimExchangeExecutor.class);
    var item = new TrpChkCustIdApimDataItem("db01", true, "DBA", "3010008", "STAFF");
    when(executor.postEnvelope(any()))
        .thenAnswer(
            invocation ->
                Mono.just(
                    new ApimResponseEnvelope<>(
                        new ApimResponseBody<>("err.tpa.cust.id.not.registered", List.of(item)))));
    var transport = new SharedApimTrpChkCustIdTransport(executor);
    var command =
        new TrpChkCustIdTransportCommand(
            new TrpChkCustIdApimRequest("cust-1"), ActorUserRole.ROLE_USER_MBR, "req-123");

    StepVerifier.create(transport.post(command))
        .assertNext(
            result -> {
              assertThat(result.errMessage()).isEqualTo("err.tpa.cust.id.not.registered");
              assertThat(result.data())
                  .singleElement()
                  .satisfies(
                      data -> {
                        assertThat(data.customerId()).isEqualTo("db01");
                        assertThat(data.userId()).isEqualTo("3010008");
                      });
            })
        .verifyComplete();

    @SuppressWarnings("rawtypes")
    ArgumentCaptor<ApimExchangeRequest> captor = ArgumentCaptor.forClass(ApimExchangeRequest.class);
    verify(executor).postEnvelope(captor.capture());
    ApimExchangeRequest<?, ?> exchange = captor.getValue();
    assertThat(exchange.path()).isEqualTo(SharedApimTrpChkCustIdTransport.API_NAME);
    assertThat(exchange.request()).isEqualTo(new TrpChkCustIdApimRequest("cust-1"));
    assertThat(exchange.requestType()).isEqualTo(TrpChkCustIdApimRequest.class);
    assertThat(exchange.responseDataType()).isEqualTo(TrpChkCustIdApimDataItem.class);
    assertThat(exchange.databaseScope()).isEqualTo(ApimDatabaseScope.NON_ACCOUNT);
    assertThat(exchange.credentialContext().actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_MBR);
    assertThat(exchange.credentialContext().accountEnv()).isNull();
    assertThat(exchange.retryEligibility()).isEqualTo(ApimRetryEligibility.SAFE_BUSINESS_CALL);
  }

  @Test
  void rejectsMissingResponseEnvelope() {
    ApimExchangeExecutor executor = mock(ApimExchangeExecutor.class);
    when(executor.postEnvelope(any()))
        .thenAnswer(invocation -> Mono.just(new ApimResponseEnvelope<>(null)));
    var transport = new SharedApimTrpChkCustIdTransport(executor);

    StepVerifier.create(
            transport.post(
                new TrpChkCustIdTransportCommand(
                    new TrpChkCustIdApimRequest("cust-1"), ActorUserRole.ROLE_USER_MBR, "req-124")))
        .expectErrorSatisfies(error -> assertThat(error.getMessage()).contains("missing response"))
        .verify();
  }

  @Test
  void rejectsSuccessfulEnvelopeWithoutData() {
    ApimExchangeExecutor executor = mock(ApimExchangeExecutor.class);
    when(executor.postEnvelope(any()))
        .thenAnswer(
            invocation ->
                Mono.just(new ApimResponseEnvelope<>(new ApimResponseBody<>(null, List.of()))));
    var transport = new SharedApimTrpChkCustIdTransport(executor);

    StepVerifier.create(
            transport.post(
                new TrpChkCustIdTransportCommand(
                    new TrpChkCustIdApimRequest("cust-1"), ActorUserRole.ROLE_USER_MBR, "req-125")))
        .expectErrorSatisfies(
            error -> assertThat(error.getMessage()).contains("invalid/malformed response"))
        .verify();
  }

  @Test
  void forwardsExplicitTerminalOwnershipIntoExchangeRequest() {
    ApimExchangeExecutor executor = mock(ApimExchangeExecutor.class);
    var item = new TrpChkCustIdApimDataItem("db01", true, "DBA", "3010008", "STAFF");
    when(executor.postEnvelope(any()))
        .thenReturn(
            Mono.just(new ApimResponseEnvelope<>(new ApimResponseBody<>("", List.of(item)))));
    var transport = new SharedApimTrpChkCustIdTransport(executor);
    var command =
        new TrpChkCustIdTransportCommand(
            new TrpChkCustIdApimRequest("cust-1"),
            ActorUserRole.ROLE_USER_MBR,
            "req-terminal",
            com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimIncidentOwnership
                .TERMINAL_CALLER);

    StepVerifier.create(transport.post(command)).expectNextCount(1).verifyComplete();

    @SuppressWarnings("rawtypes")
    ArgumentCaptor<ApimExchangeRequest> captor = ArgumentCaptor.forClass(ApimExchangeRequest.class);
    verify(executor).postEnvelope(captor.capture());
    assertThat(captor.getValue().incidentOwnership())
        .isEqualTo(
            com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimIncidentOwnership
                .TERMINAL_CALLER);
  }
}
