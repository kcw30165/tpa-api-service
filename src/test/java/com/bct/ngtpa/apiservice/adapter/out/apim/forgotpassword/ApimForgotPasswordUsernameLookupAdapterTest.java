package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.SharedApimTrpChkCustIdTransport;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpChkCustIdApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpChkCustIdApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.ForgotPasswordUsernameLookupStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.StartForgotPasswordUsernameLookupCommand;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ApimForgotPasswordUsernameLookupAdapterTest {
    private final ApimExchangeExecutor executor = mock(ApimExchangeExecutor.class);
    private final ApimForgotPasswordUsernameLookupAdapter adapter =
            new ApimForgotPasswordUsernameLookupAdapter(executor, Map.of("ID", MemberIdentityType.ID));

    @Test
    void invokesExecutorWithExplicitPilotRoutingAndMapsConfirmedResult() {
        var item = new TrpChkCustIdApimDataItem("cust-1", true, "scheme", "user-1", "ID");
        when(executor.postEnvelope(any())).thenAnswer(invocation ->
                Mono.just(envelope(null, List.of(item))));
        var command = new StartForgotPasswordUsernameLookupCommand("member01", "req-1", ActorUserRole.ROLE_USER_MBR);

        StepVerifier.create(adapter.lookup(command))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(ForgotPasswordUsernameLookupStatus.CONFIRMED);
                    assertThat(result.customerId()).isEqualTo("cust-1");
                    assertThat(result.userId()).isEqualTo("user-1");
                    assertThat(result.identityType()).isEqualTo(MemberIdentityType.ID);
                }).verifyComplete();

        ArgumentCaptor<ApimExchangeRequest> capture = ArgumentCaptor.forClass(ApimExchangeRequest.class);
        org.mockito.Mockito.verify(executor).postEnvelope(capture.capture());
        ApimExchangeRequest<?, ?> request = capture.getValue();
        assertThat(request.path()).isEqualTo(SharedApimTrpChkCustIdTransport.API_NAME);
        assertThat(request.request()).isEqualTo(new TrpChkCustIdApimRequest("member01"));
        assertThat(request.requestType()).isEqualTo(TrpChkCustIdApimRequest.class);
        assertThat(request.responseDataType()).isEqualTo(TrpChkCustIdApimDataItem.class);
        assertThat(request.databaseScope()).isEqualTo(ApimDatabaseScope.NON_ACCOUNT);
        assertThat(request.credentialContext().actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_MBR);
        assertThat(request.credentialContext().accountEnv()).isNull();
        assertThat(request.retryEligibility()).isEqualTo(ApimRetryEligibility.SAFE_BUSINESS_CALL);
    }

    @Test
    void preservesRecognizedDownstreamBusinessErrorMapping() {
        when(executor.postEnvelope(any())).thenAnswer(invocation -> Mono.just(
                envelope("err.tpa.cust.id.not.registered",
                        List.of(new TrpChkCustIdApimDataItem("", false)))));
        StepVerifier.create(adapter.lookup(new StartForgotPasswordUsernameLookupCommand(
                        "missing", "req-2", ActorUserRole.ROLE_USER_MBR)))
                .assertNext(result -> assertThat(result.status()).isEqualTo(ForgotPasswordUsernameLookupStatus.NOT_FOUND))
                .verifyComplete();
    }

    @Test
    void malformedEnvelopeStillFailsAtFeatureBoundary() {
        when(executor.postEnvelope(any())).thenAnswer(invocation ->
                Mono.just(envelope(null, List.of())));
        StepVerifier.create(adapter.lookup(new StartForgotPasswordUsernameLookupCommand(
                        "member01", "req-3", ActorUserRole.ROLE_USER_MBR)))
                .expectErrorSatisfies(error -> assertThat(error.getMessage()).contains("invalid/malformed response"))
                .verify();
    }

    private ApimResponseEnvelope<TrpChkCustIdApimDataItem> envelope(
            String error, List<TrpChkCustIdApimDataItem> data) {
        return new ApimResponseEnvelope<>(new ApimResponseBody<>(error, data));
    }
}
