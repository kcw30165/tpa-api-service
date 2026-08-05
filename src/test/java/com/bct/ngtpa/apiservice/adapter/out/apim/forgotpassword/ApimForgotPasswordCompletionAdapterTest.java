package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.dto.TrpResetPasswordApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.dto.TrpResetPasswordApimRequest;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.ForgotPasswordCompletionCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.ForgotPasswordCompletionResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ApimForgotPasswordCompletionAdapterTest {
    @Test
    void postsExactContractAndMapsSuccess() {
        var transport = new Capturing();
        var adapter = new ApimForgotPasswordCompletionAdapter(transport);

        StepVerifier.create(adapter.complete(command()))
                .assertNext(result -> assertThat(result.status())
                        .isEqualTo(ForgotPasswordCompletionResult.Status.RESET))
                .verifyComplete();

        assertThat(transport.path).isEqualTo("/ws/NGTPA/v1/TRPResetPassword");
        assertThat(transport.request.customerId()).isEqualTo("db655");
        assertThat(transport.request.password()).isEqualTo("Password!");
        assertThat(transport.request.toString()).doesNotContain("db655", "Password!");
    }

    @Test
    void preservesRecognizedPasswordPolicyRejection() {
        var adapter = new ApimForgotPasswordCompletionAdapter(
                (path, request) -> Mono.just(new TrpResetPasswordApimEnvelope("err.tpa.password.invalid", List.of())));

        StepVerifier.create(adapter.complete(command()))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(ForgotPasswordCompletionResult.Status.REJECTED);
                    assertThat(result.downstreamErrorCode()).isEqualTo("err.tpa.password.invalid");
                })
                .verifyComplete();
    }

    @Test
    void preservesRecognizedMemberNotFoundRejection() {
        var adapter = new ApimForgotPasswordCompletionAdapter(
                (path, request) -> Mono.just(new TrpResetPasswordApimEnvelope("err.tpa.member.not.found", List.of())));

        StepVerifier.create(adapter.complete(command()))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(ForgotPasswordCompletionResult.Status.REJECTED);
                    assertThat(result.downstreamErrorCode()).isEqualTo("err.tpa.member.not.found");
                })
                .verifyComplete();
    }

    private ForgotPasswordCompletionCommand command() {
        return new ForgotPasswordCompletionCommand("db655", "Password!", "req-1");
    }

    private static final class Capturing implements ApimForgotPasswordCompletionTransport {
        String path;
        TrpResetPasswordApimRequest request;

        @Override
        public Mono<TrpResetPasswordApimEnvelope> postResetPassword(String path, TrpResetPasswordApimRequest request) {
            this.path = path;
            this.request = request;
            return Mono.just(new TrpResetPasswordApimEnvelope(
                    "",
                    List.of(new TrpResetPasswordApimDataItem(true, "db655", "29/07/2026", "14:25:07"))));
        }
    }
}