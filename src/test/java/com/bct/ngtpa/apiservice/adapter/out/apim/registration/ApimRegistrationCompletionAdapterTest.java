package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import static org.assertj.core.api.Assertions.assertThat;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ApimRegistrationCompletionAdapterTest {
    @Test
    void postsExactContractAndMapsSuccess() {
        var transport = new Capturing();
        var adapter = new ApimRegistrationCompletionAdapter(transport);
        StepVerifier.create(adapter.complete(command()))
                .assertNext(r -> assertThat(r.status()).isEqualTo(RegistrationCompletionResult.Status.REGISTERED))
                .verifyComplete();
        assertThat(transport.path).isEqualTo("/ws/NGTPA/v1/TRPRegMember");
        assertThat(transport.request.customerId()).isEqualTo("db02");
        assertThat(transport.request.userId()).isEqualTo("3011825");
        assertThat(transport.request.toString()).doesNotContain("db02", "3011825", "Password1!");
    }

    @Test
    void preservesAuthoritativeBusinessError() {
        var adapter = new ApimRegistrationCompletionAdapter(
                (p, r) -> Mono.just(new TrpRegMemberApimEnvelope("err.tpa.cust.id.duplicated", List.of())));
        StepVerifier.create(adapter.complete(command())).assertNext(r -> {
            assertThat(r.status()).isEqualTo(RegistrationCompletionResult.Status.REJECTED);
            assertThat(r.downstreamErrorCode()).isEqualTo("err.tpa.cust.id.duplicated");
        }).verifyComplete();
    }

    private RegistrationCompletionCommand command() {
        return new RegistrationCompletionCommand("DB", "DBA", "db02", "3011825", "Password1!", "15/07/1969", "66290597",
                "", "req-1");
    }

    private static class Capturing implements ApimRegistrationCompletionTransport {
        String path;
        TrpRegMemberApimRequest request;

        public Mono<TrpRegMemberApimEnvelope> postRegMember(String p, TrpRegMemberApimRequest r) {
            path = p;
            request = r;
            return Mono.just(new TrpRegMemberApimEnvelope("",
                    List.of(new TrpRegMemberApimDataItem(true, "db02", "08/07/2026", "16:49:23"))));
        }
    }
}
