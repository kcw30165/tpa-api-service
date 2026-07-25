package com.bct.ngtpa.apiservice.config.auth.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.session.RevokeAuthenticatedSessionUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.logout.AuthenticatedLogoutCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.logout.AuthenticatedLogoutService;
import com.bct.ngtpa.apiservice.application.usecase.auth.logout.AuthenticatedLogoutStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.logout.AuthenticatedLogoutUseCase;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionLifecycleResult;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionLifecycleStatus;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuthenticatedLogoutRuntimeCompositionTest {
    @Test
    void compositionConnectsLogoutOnlyToRevocationCapability() {
        AtomicBoolean invoked = new AtomicBoolean();
        RevokeAuthenticatedSessionUseCase revoke = command -> {
            invoked.set(true);
            return Mono.just(AuthenticatedSessionLifecycleResult.of(
                    AuthenticatedSessionLifecycleStatus.REVOKED));
        };

        AuthenticatedLogoutUseCase useCase =
                new AuthenticatedSessionConfig().authenticatedLogoutUseCase(revoke);

        assertThat(useCase).isInstanceOf(AuthenticatedLogoutService.class);
        StepVerifier.create(useCase.logout(new AuthenticatedLogoutCommand(
                        new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx"),
                        "req-1")))
                .assertNext(result -> assertThat(result.status())
                        .isEqualTo(AuthenticatedLogoutStatus.LOGGED_OUT))
                .verifyComplete();
        assertThat(invoked).isTrue();
    }
}
