package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpChkCustIdApimRequest;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationSelectedTrust;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.RegistrationUsernameAvailabilityCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.RegistrationUsernameAvailabilityStatus;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ApimRegistrationUsernameAvailabilityAdapterTest {
    private static final String EXPECTED_PATH = "/ws/NGTPA/v1/TRPChkCustId";

    @Test
    void postsToTrpChkCustIdAndMapsSuccessToAvailable() {
        AtomicReference<String> capturedPath = new AtomicReference<>();
        AtomicReference<TrpChkCustIdApimRequest> capturedRequest = new AtomicReference<>();
        var adapter = new ApimRegistrationUsernameAvailabilityAdapter((path, request) -> {
            capturedPath.set(path);
            capturedRequest.set(request);
            return Mono.just(TrpChkCustIdApimEnvelope.success());
        });

        StepVerifier.create(adapter.checkAvailability(command("abc1@")))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(RegistrationUsernameAvailabilityStatus.AVAILABLE);
                    assertThat(result.downstreamErrorCode()).isNull();
                })
                .verifyComplete();

        assertThat(capturedPath.get()).isEqualTo(EXPECTED_PATH);
        assertThat(capturedRequest.get().trustCode()).isEqualTo("TL");
        assertThat(capturedRequest.get().accountEnv()).isEqualTo("ENV1");
        assertThat(capturedRequest.get().customerId()).isEqualTo("abc1@");
    }

    @Test
    void mapsBusinessErrMessageToRejectedWithRawDownstreamCode() {
        var adapter = new ApimRegistrationUsernameAvailabilityAdapter((path, request) ->
                Mono.just(TrpChkCustIdApimEnvelope.businessRejected("err.tpa.username.taken")));

        StepVerifier.create(adapter.checkAvailability(command("abc1@")))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(RegistrationUsernameAvailabilityStatus.REJECTED);
                    assertThat(result.downstreamErrorCode()).isEqualTo("err.tpa.username.taken");
                    assertThat(result.downstreamErrorCode()).isNotEqualTo("registration.username.isregistered");
                })
                .verifyComplete();
    }

    @Test
    void mapsUnknownBusinessErrMessageToRejectedForLaterPageFallbackClassification() {
        var adapter = new ApimRegistrationUsernameAvailabilityAdapter((path, request) ->
                Mono.just(TrpChkCustIdApimEnvelope.businessRejected("err.tpa.some.unknown.error")));

        StepVerifier.create(adapter.checkAvailability(command("abc1@")))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(RegistrationUsernameAvailabilityStatus.REJECTED);
                    assertThat(result.downstreamErrorCode()).isEqualTo("err.tpa.some.unknown.error");
                })
                .verifyComplete();
    }

    @Test
    void technicalFailureDoesNotBecomeBusinessRejectedOrGenericBusinessFallback() {
        var adapter = new ApimRegistrationUsernameAvailabilityAdapter((path, request) ->
                Mono.error(new IllegalStateException("transport timeout")));

        StepVerifier.create(adapter.checkAvailability(command("abc1@")))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("transport timeout"))
                .verify();
    }

    @Test
    void rawUsernameIsRedactedFromCommandAndAdapterRequestToString() {
        var command = command("abc1@");
        var request = new com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpChkCustIdApimMapper()
                .toApimRequest(command);

        assertThat(command.toString()).doesNotContain("abc1@");
        assertThat(request.toString()).doesNotContain("abc1@");
    }

    private RegistrationUsernameAvailabilityCommand command(String username) {
        return new RegistrationUsernameAvailabilityCommand(
                new RegistrationSelectedTrust("TL", "ENV1"),
                username,
                "req-1");
    }
}
