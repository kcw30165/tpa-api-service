package com.bct.ngtpa.apiservice.adapter.out.apim.login;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.login.dto.TrpMemberLoginApimDataItem;
import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TrpMemberLoginAdapterTest {

    @Test
    void authenticatedOutcomeRequiresExactlyOneSuccessfulDataItem() {
        var item = new TrpMemberLoginApimDataItem(true, "role_user_mbr", 0, List.of());
        TrpMemberLoginTransport transport = command -> Mono.just(envelope("", List.of(item)));
        var adapter = new TrpMemberLoginAdapter(transport, new TrpMemberLoginContractMapper());

        StepVerifier.create(adapter.authenticate(command()))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(CredentialAuthenticationStatus.AUTHENTICATED);
                    assertThat(result.normalizedUsername()).isEqualTo("member");
                })
                .verifyComplete();
    }

    @Test
    void mapsConfirmedProgressErrorsToStableApplicationOutcomes() {
        assertOutcome("err.tpa.wrong.password", CredentialAuthenticationStatus.INCORRECT_PASSWORD);
        assertOutcome("err.tpa.cust.id.not.registered", CredentialAuthenticationStatus.INCORRECT_USERNAME);
        assertOutcome("err.tpa.no.valid.account", CredentialAuthenticationStatus.INVALID_ACCOUNT);
        assertOutcome("err.tpa.account.locked", CredentialAuthenticationStatus.ACCOUNT_LOCKED);
    }

    @Test
    void emptyUnknownOrContradictoryEnvelopeIsUnavailable() {
        assertOutcome("", CredentialAuthenticationStatus.UNAVAILABLE);
        assertOutcome("err.tpa.unknown", CredentialAuthenticationStatus.UNAVAILABLE);

        var item = new TrpMemberLoginApimDataItem(true, "role_user_mbr", 0, List.of());
        TrpMemberLoginTransport contradictory = command -> Mono.just(
                envelope("err.tpa.wrong.password", List.of(item)));
        StepVerifier.create(new TrpMemberLoginAdapter(
                contradictory, new TrpMemberLoginContractMapper()).authenticate(command()))
                .assertNext(result -> assertThat(result.status())
                        .isEqualTo(CredentialAuthenticationStatus.UNAVAILABLE))
                .verifyComplete();
    }

    @Test
    void multipleDataItemsAreUnavailable() {
        var item = new TrpMemberLoginApimDataItem(true, "role_user_mbr", 0, List.of());
        TrpMemberLoginTransport transport = command -> Mono.just(envelope("", List.of(item, item)));
        StepVerifier.create(new TrpMemberLoginAdapter(
                transport, new TrpMemberLoginContractMapper()).authenticate(command()))
                .assertNext(result -> assertThat(result.status())
                        .isEqualTo(CredentialAuthenticationStatus.UNAVAILABLE))
                .verifyComplete();
    }

    private void assertOutcome(String errMessage, CredentialAuthenticationStatus expected) {
        TrpMemberLoginTransport transport = command -> Mono.just(envelope(errMessage, List.of()));
        StepVerifier.create(new TrpMemberLoginAdapter(
                transport, new TrpMemberLoginContractMapper()).authenticate(command()))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(expected);
                    assertThat(result.downstreamCode()).isNull();
                })
                .verifyComplete();
    }

    private CredentialAuthenticationCommand command() {
        return new CredentialAuthenticationCommand("member", "Exact", "en", "req-1", com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR);
    }

    private ApimResponseEnvelope<TrpMemberLoginApimDataItem> envelope(
            String error,
            List<TrpMemberLoginApimDataItem> data) {
        return ApimResponseEnvelope.<TrpMemberLoginApimDataItem>builder()
                .response(ApimResponseBody.<TrpMemberLoginApimDataItem>builder()
                        .errMessage(error)
                        .data(data)
                        .build())
                .build();
    }
}
