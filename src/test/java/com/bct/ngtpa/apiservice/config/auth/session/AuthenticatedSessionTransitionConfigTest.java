package com.bct.ngtpa.apiservice.config.auth.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.application.port.in.EstablishAuthenticatedLoginSessionUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.session.DeleteAuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.session.EstablishAuthenticatedLoginSessionService;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionLifecycleService;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.SecureAuthenticatedSessionIdGenerator;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AuthenticatedSessionTransitionConfigTest {
    @Test
    void compositionUsesConfiguredIdleAndAbsoluteSessionDurations() {
        var config = new AuthenticatedSessionConfig();
        var properties = new AuthWorkflowProperties();
        var login = new AuthWorkflowProperties.Login();
        login.getSession().setIdleTimeoutSeconds(1800);
        login.getSession().setAbsoluteTimeoutSeconds(28800);
        login.getSession().setTerminalRetentionSeconds(300);
        properties.setLogin(login);

        AuthenticatedSessionLifecycleService lifecycle = config.authenticatedSessionLifecycleService(
                Mockito.mock(AuthenticatedSessionPort.class),
                Mockito.mock(AuthenticatedSessionProtectorPort.class),
                new SecureAuthenticatedSessionIdGenerator(),
                Clock.systemUTC(),
                properties);

        assertThat(lifecycle).isNotNull();
    }

    @Test
    void compositionRejectsInvalidSessionDurationsWithoutRequiringUnrelatedLoginPolicy() {
        var config = new AuthenticatedSessionConfig();
        var properties = new AuthWorkflowProperties();
        var login = new AuthWorkflowProperties.Login();
        login.getSession().setIdleTimeoutSeconds(1801);
        login.getSession().setAbsoluteTimeoutSeconds(1800);
        login.getSession().setTerminalRetentionSeconds(300);
        properties.setLogin(login);

        assertThatThrownBy(() -> config.authenticatedSessionLifecycleService(
                Mockito.mock(AuthenticatedSessionPort.class),
                Mockito.mock(AuthenticatedSessionProtectorPort.class),
                new SecureAuthenticatedSessionIdGenerator(),
                Clock.systemUTC(),
                properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("idle-timeout-seconds must not exceed absolute-timeout-seconds");
    }

    @Test
    void transitionCompositionAdaptsOnlyDeleteFromPreAuthPort() {
        var config = new AuthenticatedSessionConfig();
        AuthFlowSessionPort preAuth = Mockito.mock(AuthFlowSessionPort.class);
        AuthenticatedSessionLifecycleService lifecycle = Mockito.mock(AuthenticatedSessionLifecycleService.class);

        DeleteAuthFlowSessionPort cleanup = config.deleteAuthFlowSessionPort(preAuth);
        EstablishAuthenticatedLoginSessionUseCase transition =
                config.establishAuthenticatedLoginSessionUseCase(
                lifecycle,
                cleanup,
                () -> "authenticated_csrf_token",
                new com.bct.ngtpa.apiservice.application.usecase.session.authflow.Sha256CsrfTokenHasher());

        assertThat(cleanup).isNotNull();
        assertThat(transition).isInstanceOf(EstablishAuthenticatedLoginSessionService.class);
    }
}
