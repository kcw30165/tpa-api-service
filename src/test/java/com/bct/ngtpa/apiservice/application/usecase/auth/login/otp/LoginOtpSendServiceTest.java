package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.*;
import com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.*;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.*;
import com.bct.ngtpa.apiservice.application.dto.message.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.service.security.ratelimit.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class LoginOtpSendServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-24T01:00:00Z");
    private static final SendLoginOtpCommand COMMAND = new SendLoginOtpCommand(
            "afs_opaque", "csrf_opaque", "dst_opaque", "idem_opaque",
            "https://portal.example", null, "en", "req-1");

    @Test
    void createsSharedChallengeDeliversRawCodeAndBindsChallengeToSession() {
        List<String> order = new ArrayList<>();
        AtomicReference<CreateOtpChallengeCommand> create = new AtomicReference<>();
        AtomicReference<MessageDeliveryCommand> delivery = new AtomicReference<>();
        AtomicReference<LoginOtpSessionBindingCommand> binding = new AtomicReference<>();

        LoginProtectedAuthFlowContextLoader loader = command -> {
            order.add("load");
            return Mono.just(LoginProtectedAuthFlowContextResult.valid(context(), null, null,"csrf_hash"));
        };
        OtpChallengePort otp = new StubOtpPort() {
            public Mono<CreateOtpChallengeResult> createChallenge(CreateOtpChallengeCommand command) {
                order.add("create"); create.set(command);
                return Mono.just(new CreateOtpChallengeResult(
                        "otp_opaque", "123456", NOW.plusSeconds(300), 300));
            }
        };
        MessageDeliveryPort messages = command -> {
            order.add("deliver"); delivery.set(command);
            return Mono.just(new MessageDeliveryResult(
                    MessageDeliveryStatus.ACCEPTED, command.channel(), "provider-id", null, null));
        };
        LoginOtpSessionBindingPort sessions = command -> {
            order.add("bind"); binding.set(command); return Mono.empty();
        };

        var service = service(loader, otp, messages, sessions);
        StepVerifier.create(service.send(COMMAND))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(SendLoginOtpStatus.SENT);
                    assertThat(result.maskedDestination()).isEqualTo("****4567");
                    assertThat(result.expiresInSeconds()).isEqualTo(300);
                    assertThat(result.resendCooldownSeconds()).isEqualTo(60);
                }).verifyComplete();

        assertThat(order).containsExactly("load", "create", "deliver", "bind");
        assertThat(create.get().scenario()).isEqualTo(OtpScenario.LOGIN);
        assertThat(create.get().workflowRef()).isEqualTo("afs_opaque");
        assertThat(create.get().channel()).isEqualTo(OtpChannel.SMS);
        assertThat(create.get().maxResendAttempts()).isZero();
        assertThat(delivery.get().scenario()).isEqualTo(MessageDeliveryScenario.LOGIN);
        assertThat(delivery.get().messageType()).isEqualTo(MessageDeliveryType.LOGIN_OTP);
        assertThat(delivery.get().recipient()).isEqualTo("91234567");
        assertThat(delivery.get().templateVariables()).containsEntry("code", "123456");
        assertThat(binding.get().newOtpChallengeId()).isEqualTo("otp_opaque");
        assertThat(service.toString()).doesNotContain("123456", "91234567");
    }

    @Test
    void invalidProtectedContextProducesNoChallengeOrDelivery() {
        LoginProtectedAuthFlowContextLoader loader = command -> Mono.just(
                LoginProtectedAuthFlowContextResult.invalid(
                        LoginProtectedAuthFlowContextStatus.CSRF_INVALID));
        AtomicReference<Boolean> called = new AtomicReference<>(false);
        OtpChallengePort otp = new StubOtpPort() {
            public Mono<CreateOtpChallengeResult> createChallenge(CreateOtpChallengeCommand command) {
                called.set(true); return Mono.error(new AssertionError("must not create"));
            }
        };
        var result = service(loader, otp, command -> Mono.error(new AssertionError("must not deliver")),
                command -> Mono.error(new AssertionError("must not bind")));

        StepVerifier.create(result.send(COMMAND))
                .assertNext(value -> assertThat(value.status())
                        .isEqualTo(SendLoginOtpStatus.AUTH_FLOW_INVALID))
                .verifyComplete();
        assertThat(called.get()).isFalse();
    }

    @Test
    void deliveryFailureInvalidatesNewChallengeAndDoesNotBindSession() {
        List<String> order = new ArrayList<>();
        OtpChallengePort otp = new StubOtpPort() {
            public Mono<CreateOtpChallengeResult> createChallenge(CreateOtpChallengeCommand command) {
                order.add("create");
                return Mono.just(new CreateOtpChallengeResult(
                        "otp_opaque", "123456", NOW.plusSeconds(300), 300));
            }
            public Mono<Void> invalidateChallenge(InvalidateOtpChallengeCommand command) {
                order.add("invalidate"); return Mono.empty();
            }
        };
        var service = service(command -> Mono.just(
                        LoginProtectedAuthFlowContextResult.valid(context(), null, null,"csrf_hash")), otp,
                command -> {
                    order.add("deliver");
                    return Mono.just(new MessageDeliveryResult(
                            MessageDeliveryStatus.FAILED, command.channel(), null,
                            "provider-secret", "provider detail"));
                },
                command -> Mono.error(new AssertionError("must not bind")));

        StepVerifier.create(service.send(COMMAND))
                .assertNext(value -> {
                    assertThat(value.status()).isEqualTo(SendLoginOtpStatus.DELIVERY_FAILED);
                    assertThat(value.toString()).doesNotContain(
                            "provider-secret", "provider detail", "123456");
                }).verifyComplete();
        assertThat(order).containsExactly("create", "deliver", "invalidate");
    }

    private LoginOtpSendService service(
            LoginProtectedAuthFlowContextLoader loader,
            OtpChallengePort otp,
            MessageDeliveryPort messages,
            LoginOtpSessionBindingPort sessions) {
        return new LoginOtpSendService(loader, new LoginOtpDestinationResolver(), otp, messages,
                sessions, new DestinationBucketIdGenerator() {
                    public String generateBucketId(DestinationType type, String value) { return "hash_only"; }
                }, Clock.fixed(NOW, ZoneOffset.UTC),
                new LoginOtpSendPolicy(6, true, 300, 60, 5, false, 0,
                        "Your verification code is {code}", "Verification code"));
    }

    private LoginPreAuthContext context() {
        return new LoginPreAuthContext("member01", "MBR", null,
                Map.of("dst_opaque", new LoginPreAuthDestination(
                        "dst_opaque", "mobile", "91234567", "****4567")),
                NOW.minusSeconds(60), "v1");
    }

    private abstract static class StubOtpPort implements OtpChallengePort {
        public Mono<CreateOtpChallengeResult> createChallenge(CreateOtpChallengeCommand c) { return Mono.error(new UnsupportedOperationException()); }
        public Mono<VerifyOtpChallengeResult> verifyChallenge(VerifyOtpChallengeCommand c) { return Mono.error(new UnsupportedOperationException()); }
        public Mono<ReplaceOtpChallengeResult> replaceChallenge(ReplaceOtpChallengeCommand c) { return Mono.error(new UnsupportedOperationException()); }
        public Mono<Void> invalidateChallenge(InvalidateOtpChallengeCommand c) { return Mono.empty(); }
    }
}
