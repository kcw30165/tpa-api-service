package com.bct.ngtpa.apiservice.config.auth.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.port.out.PortalAccessContextPort;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedAccountContextResolver;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionLifecycleResult;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionLifecycleStatus;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.LoadAndTouchAuthenticatedSessionUseCase;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuthenticatedPortalContextCompositionTest {
    @Test
    void primaryPortalContextPortUsesAuthenticatedLifecycle() {
        var seenSession = new java.util.concurrent.atomic.AtomicReference<AuthenticatedSessionId>();
        LoadAndTouchAuthenticatedSessionUseCase lifecycle = command -> {
            seenSession.set(command.authenticatedSessionId());
            return Mono.just(AuthenticatedSessionLifecycleResult.of(
                    AuthenticatedSessionLifecycleStatus.NOT_FOUND));
        };

        PortalAccessContextPort port = new AuthenticatedSessionConfig().authenticatedPortalAccessContextPort(lifecycle);

        assertThat(port).isInstanceOf(AuthenticatedAccountContextResolver.class);
        StepVerifier.create(port.resolvePortalAccessContext(
                "acc_abcdefghijklmnopqrstuvwx",
                "sid_abcdefghijklmnopqrstuvwx"))
                .expectError()
                .verify();
        assertThat(seenSession.get().value()).isEqualTo("sid_abcdefghijklmnopqrstuvwx");
    }
}
