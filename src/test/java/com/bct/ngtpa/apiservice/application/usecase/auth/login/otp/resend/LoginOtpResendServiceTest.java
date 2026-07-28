package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.resend;

import static org.assertj.core.api.Assertions.assertThat;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.*;
import com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.LoginPreAuthContext;
import com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.LoginPreAuthDestination;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.*;
import com.bct.ngtpa.apiservice.application.dto.message.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import java.time.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class LoginOtpResendServiceTest {
    static final Instant NOW = Instant.parse("2026-07-24T04:00:00Z");
    static final ResendLoginOtpCommand COMMAND = new ResendLoginOtpCommand("afs_secret", "csrf_secret", "idem_key_123",
            "https://portal", null, "en", "req");

    @Test
    void replacesDeliversThenBindsNewChallenge() {
        var delivered = new AtomicReference<MessageDeliveryCommand>();
        var bound = new AtomicReference<LoginOtpSessionBindingCommand>();
        OtpChallengePort otp = new Stub() {
            public Mono<ReplaceOtpChallengeResult> replaceChallenge(ReplaceOtpChallengeCommand c) {
                return Mono.just(new ReplaceOtpChallengeResult("old", "new", "123456", OtpReplacementOutcome.REPLACED,
                        NOW.plusSeconds(300), 300, 60, 0, null));
            }
        };
        var service = service(
                c -> Mono.just(LoginProtectedAuthFlowContextResult.valid(
                        new LoginPreAuthContext("member", "MBR", null, java.util.Map.of(), NOW, "v1"), "old",
                        new LoginPreAuthDestination("dst_selected", "mobile", "91234567", "****4567"), "csrf_hash")),
                otp, c -> {
                    delivered.set(c);
                    return Mono.just(
                            new MessageDeliveryResult(MessageDeliveryStatus.ACCEPTED, c.channel(), null, null, null));
                }, c -> {
                    bound.set(c);
                    return Mono.empty();
                });
        StepVerifier.create(service.resend(COMMAND)).assertNext(r -> {
            assertThat(r.status()).isEqualTo(ResendLoginOtpStatus.RESENT);
            assertThat(r.maskedDestination()).isEqualTo("****4567");
        }).verifyComplete();
        assertThat(delivered.get().templateVariables()).containsEntry("code", "123456");
        assertThat(bound.get().newOtpChallengeId()).isEqualTo("new");
    }

    @Test
    void cooldownDoesNotDeliverOrBind() {
        OtpChallengePort otp = new Stub() {
            public Mono<ReplaceOtpChallengeResult> replaceChallenge(ReplaceOtpChallengeCommand c) {
                return Mono.just(new ReplaceOtpChallengeResult("old", null, null, OtpReplacementOutcome.COOLDOWN_ACTIVE,
                        null, 0, 60, 0, 25));
            }
        };
        var service = service(
                c -> Mono.just(LoginProtectedAuthFlowContextResult
                        .valid(new LoginPreAuthContext("member", "MBR", null, java.util.Map.of(), NOW, "v1"), "old",
                                new LoginPreAuthDestination("dst_selected", "email", "member@example.com",
                                        "m***@example.com"),
                                "csrf_hash")),
                otp, c -> Mono.error(new AssertionError()), c -> Mono.error(new AssertionError()));
        StepVerifier.create(service.resend(COMMAND)).assertNext(r -> {
            assertThat(r.status()).isEqualTo(ResendLoginOtpStatus.COOLDOWN_ACTIVE);
            assertThat(r.retryAfterSeconds()).isEqualTo(25);
        }).verifyComplete();
    }

    @Test
    void invalidContextHasNoOtpSideEffects() {
        var service = service(
                c -> Mono.just(
                        LoginProtectedAuthFlowContextResult.invalid(LoginProtectedAuthFlowContextStatus.CSRF_INVALID)),
                new Stub() {
                }, c -> Mono.error(new AssertionError()), c -> Mono.error(new AssertionError()));
        StepVerifier.create(service.resend(COMMAND))
                .assertNext(r -> {
                    assertThat(r.status()).isEqualTo(ResendLoginOtpStatus.CSRF_INVALID);
                    assertThat(r.maskedDestination()).isNull();
                    assertThat(r.expiresInSeconds()).isNull();
                    assertThat(r.resendCooldownSeconds()).isNull();
                    assertThat(r.retryAfterSeconds()).isNull();
                })
                .verifyComplete();
    }

    private LoginOtpResendService service(LoginProtectedAuthFlowContextLoader l, OtpChallengePort o,
            MessageDeliveryPort m,
            LoginOtpSessionBindingPort b) {
        return new LoginOtpResendService(l, o, m, b, Clock.fixed(NOW, ZoneOffset.UTC),
                new LoginOtpSendPolicy(6, true, 300, 60, 5, false, 0, "Your code is {code}", "Code"));
    }

    abstract static class Stub implements OtpChallengePort {
        public Mono<CreateOtpChallengeResult> createChallenge(CreateOtpChallengeCommand c) {
            return Mono.error(new UnsupportedOperationException());
        }

        public Mono<VerifyOtpChallengeResult> verifyChallenge(VerifyOtpChallengeCommand c) {
            return Mono.error(new UnsupportedOperationException());
        }

        public Mono<ReplaceOtpChallengeResult> replaceChallenge(ReplaceOtpChallengeCommand c) {
            return Mono.error(new UnsupportedOperationException());
        }

        public Mono<Void> invalidateChallenge(InvalidateOtpChallengeCommand c) {
            return Mono.empty();
        }
    }
}
