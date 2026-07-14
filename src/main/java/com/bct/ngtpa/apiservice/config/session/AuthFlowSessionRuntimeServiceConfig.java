package com.bct.ngtpa.apiservice.config.session;

import com.bct.ngtpa.apiservice.adapter.out.session.AuthFlowSessionCancellationAdapter;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionCancellationPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.service.session.SecureSessionIdGenerator;
import com.bct.ngtpa.apiservice.application.service.session.SessionIdGenerator;
import com.bct.ngtpa.apiservice.application.service.session.SessionTtlCalculator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionCsrfService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionLifecycleService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenGenerator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenHasher;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.SecureRandomCsrfTokenGenerator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.Sha256CsrfTokenHasher;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthFlowSessionRuntimeServiceConfig {

    @Bean
    @ConditionalOnMissingBean
    public SessionIdGenerator sessionIdGenerator() {
        return new SecureSessionIdGenerator();
    }

    @Bean
    @ConditionalOnMissingBean
    public SessionTtlCalculator sessionTtlCalculator() {
        return new SessionTtlCalculator();
    }

    @Bean
    @ConditionalOnMissingBean(AuthFlowSessionCancellationPort.class)
    public AuthFlowSessionCancellationPort authFlowSessionCancellationPort(
            AuthFlowSessionPort authFlowSessionPort) {
        return new AuthFlowSessionCancellationAdapter(authFlowSessionPort);
    }

    @Bean
    @ConditionalOnMissingBean(AuthFlowSessionLifecycleService.class)
    public AuthFlowSessionLifecycleService authFlowSessionLifecycleService(
            AuthFlowSessionPort authFlowSessionPort,
            SessionIdGenerator sessionIdGenerator,
            SessionTtlCalculator sessionTtlCalculator,
            Clock clock) {
        return new AuthFlowSessionLifecycleService(
                authFlowSessionPort,
                sessionIdGenerator,
                sessionTtlCalculator,
                clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public CsrfTokenGenerator csrfTokenGenerator() {
        return new SecureRandomCsrfTokenGenerator();
    }

    @Bean
    @ConditionalOnMissingBean
    public CsrfTokenHasher csrfTokenHasher() {
        return new Sha256CsrfTokenHasher();
    }

    @Bean
    @ConditionalOnMissingBean(AuthFlowSessionCsrfService.class)
    public AuthFlowSessionCsrfService authFlowSessionCsrfService(
            AuthFlowSessionPort authFlowSessionPort,
            CsrfTokenGenerator csrfTokenGenerator,
            CsrfTokenHasher csrfTokenHasher) {
        return new AuthFlowSessionCsrfService(
                authFlowSessionPort,
                csrfTokenGenerator,
                csrfTokenHasher);
    }
}