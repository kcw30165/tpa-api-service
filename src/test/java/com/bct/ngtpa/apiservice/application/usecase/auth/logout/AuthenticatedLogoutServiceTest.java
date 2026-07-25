package com.bct.ngtpa.apiservice.application.usecase.auth.logout;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.session.RevokeAuthenticatedSessionUseCase;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionLifecycleResult;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionLifecycleStatus;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.RevokeActiveAuthenticatedSessionCommand;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuthenticatedLogoutServiceTest {
    private static final AuthenticatedSessionId SID =
            new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");

    @Test
    void revokedSessionReturnsLoggedOut() {
        AtomicReference<RevokeActiveAuthenticatedSessionCommand> seen = new AtomicReference<>();
        RevokeAuthenticatedSessionUseCase revoke = command -> {
            seen.set(command);
            return Mono.just(AuthenticatedSessionLifecycleResult.of(
                    AuthenticatedSessionLifecycleStatus.REVOKED));
        };
        var service = new AuthenticatedLogoutService(revoke);

        StepVerifier.create(service.logout(new AuthenticatedLogoutCommand(SID, "req-1")))
                .assertNext(result -> assertThat(result.status())
                        .isEqualTo(AuthenticatedLogoutStatus.LOGGED_OUT))
                .verifyComplete();

        assertThat(seen.get().authenticatedSessionId()).isEqualTo(SID);
        assertThat(seen.get().reason()).isEqualTo("USER_LOGOUT");
        assertThat(seen.get().correlationId()).isEqualTo("req-1");
    }

    @Test
    void missingExpiredOrPreviouslyRevokedSessionIsIdempotentSuccess() {
        for (AuthenticatedSessionLifecycleStatus status : new AuthenticatedSessionLifecycleStatus[] {
                AuthenticatedSessionLifecycleStatus.NOT_FOUND,
                AuthenticatedSessionLifecycleStatus.EXPIRED
        }) {
            var service = service(status);
            StepVerifier.create(service.logout(new AuthenticatedLogoutCommand(SID, null)))
                    .assertNext(result -> assertThat(result.status())
                            .isEqualTo(AuthenticatedLogoutStatus.ALREADY_LOGGED_OUT))
                    .verifyComplete();
        }
    }

    @Test
    void lifecycleFailureAndReactiveErrorReturnFailedWithoutLeakingCause() {
        StepVerifier.create(service(AuthenticatedSessionLifecycleStatus.FAILED)
                        .logout(new AuthenticatedLogoutCommand(SID, "req-1")))
                .assertNext(result -> assertThat(result.status())
                        .isEqualTo(AuthenticatedLogoutStatus.FAILED))
                .verifyComplete();

        var service = new AuthenticatedLogoutService(command ->
                Mono.error(new IllegalStateException("redis host secret")));
        StepVerifier.create(service.logout(new AuthenticatedLogoutCommand(SID, "req-1")))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(AuthenticatedLogoutStatus.FAILED);
                    assertThat(result.toString()).doesNotContain("redis host secret", SID.value());
                })
                .verifyComplete();
    }

    private AuthenticatedLogoutService service(AuthenticatedSessionLifecycleStatus status) {
        return new AuthenticatedLogoutService(command -> Mono.just(
                AuthenticatedSessionLifecycleResult.of(status)));
    }
}
