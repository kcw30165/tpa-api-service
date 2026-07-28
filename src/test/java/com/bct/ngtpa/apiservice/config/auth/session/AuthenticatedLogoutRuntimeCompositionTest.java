package com.bct.ngtpa.apiservice.config.auth.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.usecase.auth.logout.*;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuthenticatedLogoutRuntimeCompositionTest {
    @Test
    void compositionConnectsLogoutOnlyToLoggedOutLifecycleCapability() {
        AtomicBoolean invoked = new AtomicBoolean();
        LogoutAuthenticatedSessionUseCase logout = command -> {
            invoked.set(true);
            return Mono.just(AuthenticatedSessionLifecycleResult.of(
                    AuthenticatedSessionLifecycleStatus.LOGGED_OUT));
        };

        AuthenticatedLogoutUseCase useCase =
                new AuthenticatedSessionConfig().authenticatedLogoutUseCase(logout);

        assertThat(useCase).isInstanceOf(AuthenticatedLogoutService.class);
        StepVerifier.create(useCase.logout(new AuthenticatedLogoutCommand(
                        new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx"),
                        "req-1")))
                .assertNext(result -> assertThat(result.status())
                        .isEqualTo(AuthenticatedLogoutStatus.ALREADY_LOGGED_OUT))
                .verifyComplete();
        assertThat(invoked).isTrue();
    }
}
