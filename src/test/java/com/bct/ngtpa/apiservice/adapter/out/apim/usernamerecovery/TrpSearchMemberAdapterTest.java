package com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCryptoException;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.dto.TrpSearchMemberApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.dto.TrpSearchMemberApimRequest;
import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryLookupCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryLookupResult;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

class TrpSearchMemberAdapterTest {

    private CapturingTransport transport;
    private TrpSearchMemberAdapter adapter;

    @BeforeEach
    void setUp() {
        transport = new CapturingTransport();
        transport.response = Mono.just(envelope("", List.of(registeredItem("DB", "DBA", "db02"))));
        adapter = new TrpSearchMemberAdapter(transport);
    }

    @Test
    void matchedEnvelopeMapsToMatchedResultAndDelegatesMappedRequestAndCorrelationId() {
        StepVerifier.create(adapter.search(command()).contextWrite(context("req-009")))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(UsernameRecoveryLookupResult.Status.MATCHED);
                    assertThat(result.candidates()).singleElement().satisfies(candidate -> {
                        assertThat(candidate.accountEnv()).isEqualTo("DB");
                        assertThat(candidate.trustCode()).isEqualTo("DBA");
                        assertThat(candidate.registered()).isTrue();
                        assertThat(candidate.userName()).isEqualTo("db02");
                    });
                })
                .verifyComplete();

        assertThat(transport.request).isNotNull();
        assertThat(transport.request.searchBy()).isEqualTo("STAFFNO");
        assertThat(transport.request.searchValue()).isEqualTo("3011825");
        assertThat(transport.request.dob()).isEqualTo("15/07/1969");
        assertThat(transport.request.email()).isEqualTo("member@example.com");
        assertThat(transport.request.mobileNumber()).isEmpty();
        assertThat(transport.correlationId).isEqualTo("req-009");
    }

    @Test
    void absentCorrelationIdIsDelegatedAsNull() {
        StepVerifier.create(adapter.search(command()))
                .expectNextMatches(result -> result.status() == UsernameRecoveryLookupResult.Status.MATCHED)
                .verifyComplete();
        assertThat(transport.correlationId).isNull();
    }

    @Test
    void memberNotFoundErrorMessageMapsToNotFound() {
        transport.response = Mono.just(envelope("err.tpa.member.not.found", List.of()));
        verifyStatus(UsernameRecoveryLookupResult.Status.NOT_FOUND);
    }

    @Test
    void blankSuccessWithEmptyDataMapsToNotFoundAccordingToCurrentUsernameRecoveryContract() {
        transport.response = Mono.just(envelope("", List.of()));
        verifyStatus(UsernameRecoveryLookupResult.Status.NOT_FOUND);
    }

    @Test
    void contractViolationMapsToContractInvalid() {
        transport.response = Mono.just(envelope("", List.of(registeredItem("DB", "DBA", ""))));
        verifyStatus(UsernameRecoveryLookupResult.Status.CONTRACT_INVALID);
    }

    @Test
    void nullEnvelopeMapsToUnavailable() {
        transport.response = Mono.justOrEmpty(null);
        StepVerifier.create(adapter.search(command()).contextWrite(context("req-009")))
                .verifyComplete();
    }

    @Test
    void nullResponseMapsToUnavailable() {
        transport.response = Mono.just(new ApimResponseEnvelope<>(null));
        verifyStatus(UsernameRecoveryLookupResult.Status.UNAVAILABLE);
    }

    @Test
    void unclassifiableErrorMessageMapsToUnknownOutcomeWithoutLeakingDownstreamCode() {
        transport.response = Mono.just(envelope("err.tpa.bad.request", List.of()));
        StepVerifier.create(adapter.search(command()).contextWrite(context("req-009")))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(UsernameRecoveryLookupResult.Status.UNKNOWN_OUTCOME);
                    assertThat(result.toString()).doesNotContain("err.tpa.bad.request");
                })
                .verifyComplete();
    }

    @Test
    void apimExceptionMapsToUnavailable() {
        transport.response = Mono.error(new ApimException(
                HttpStatus.BAD_GATEWAY, "APIM request failed with status=502 body=..."));
        verifyStatus(UsernameRecoveryLookupResult.Status.UNAVAILABLE);
    }

    @Test
    void connectionFailureMapsToUnavailable() {
        transport.response = Mono.error(new WebClientRequestException(
                new RuntimeException("connection refused"), HttpMethod.POST,
                URI.create(SharedApimTrpSearchMemberTransport.API_NAME), HttpHeaders.EMPTY));
        verifyStatus(UsernameRecoveryLookupResult.Status.UNAVAILABLE);
    }

    @Test
    void timeoutMapsToUnavailable() {
        transport.response = Mono.error(new TimeoutException("timed out"));
        verifyStatus(UsernameRecoveryLookupResult.Status.UNAVAILABLE);
    }

    @Test
    void cryptoFailureMapsToUnavailable() {
        transport.response = Mono.error(new ApimCryptoException("Failed to decrypt APIM response payload."));
        verifyStatus(UsernameRecoveryLookupResult.Status.UNAVAILABLE);
    }

    @Test
    void unrecognizedRuntimeExceptionIsNotSwallowed() {
        transport.response = Mono.error(new IllegalStateException("unexpected"));
        StepVerifier.create(adapter.search(command()).contextWrite(context("req-009")))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void resultNeverDisclosesIdentityDobOrContact() {
        transport.response = Mono.error(new ApimException(
                HttpStatus.BAD_GATEWAY, "APIM request failed with status=502 body=..."));
        StepVerifier.create(adapter.search(command()).contextWrite(context("req-009")))
                .assertNext(result -> assertThat(result.toString())
                        .doesNotContain("3011825", "15/07/1969", "member@example.com"))
                .verifyComplete();
    }

    private void verifyStatus(UsernameRecoveryLookupResult.Status status) {
        StepVerifier.create(adapter.search(command()).contextWrite(context("req-009")))
                .assertNext(result -> assertThat(result.status()).isEqualTo(status))
                .verifyComplete();
    }

    private static Context context(String requestId) {
        return Context.of(RequestCorrelation.REQUEST_ID_CONTEXT_KEY, requestId);
    }

    private static UsernameRecoveryLookupCommand command() {
        return new UsernameRecoveryLookupCommand(
                MemberIdentityType.STAFFNO, "3011825", "15/07/1969", "member@example.com", null);
    }

    private static ApimResponseEnvelope<TrpSearchMemberApimDataItem> envelope(
            String error, List<TrpSearchMemberApimDataItem> data) {
        return new ApimResponseEnvelope<>(new ApimResponseBody<>(error, data));
    }

    private static TrpSearchMemberApimDataItem registeredItem(
            String env, String trustCode, String custId) {
        return new TrpSearchMemberApimDataItem(
                env, trustCode, "OE", "00000008802", "5", "", "", true, custId);
    }

    private static final class CapturingTransport implements TrpSearchMemberTransport {
        private TrpSearchMemberApimRequest request;
        private String correlationId;
        private Mono<ApimResponseEnvelope<TrpSearchMemberApimDataItem>> response;

        @Override
        public Mono<ApimResponseEnvelope<TrpSearchMemberApimDataItem>> post(
                TrpSearchMemberApimRequest request, String correlationId) {
            this.request = request;
            this.correlationId = correlationId;
            return response;
        }
    }
}
