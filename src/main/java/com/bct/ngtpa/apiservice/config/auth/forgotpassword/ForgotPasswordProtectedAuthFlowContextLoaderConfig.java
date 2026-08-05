package com.bct.ngtpa.apiservice.config.auth.forgotpassword;

import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionExpiryRefreshPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.SecurityAuditEventPort;
import com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context.DefaultForgotPasswordProtectedAuthFlowContextLoader;
import com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context.ForgotPasswordProtectedAuthFlowContextLoader;
import com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context.SlidingForgotPasswordProtectedAuthFlowContextLoader;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionCsrfService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionLifecycleService;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ForgotPasswordProtectedAuthFlowContextLoaderConfig {

    @Bean
    @ConditionalOnMissingBean(ForgotPasswordProtectedAuthFlowContextLoader.class)
    public ForgotPasswordProtectedAuthFlowContextLoader forgotPasswordProtectedAuthFlowContextLoader(
            AuthFlowSessionLifecycleService lifecycleService,
            AuthFlowSessionCsrfService csrfService,
            ForgotPasswordAuthFlowContextProtectorPort contextProtector,
            SecurityAuditEventPort securityAuditEventPort,
            AuthFlowSessionExpiryRefreshPort expiryRefreshPort,
            Clock clock) {
        ForgotPasswordProtectedAuthFlowContextLoader delegate =
                new DefaultForgotPasswordProtectedAuthFlowContextLoader(
                        lifecycleService,
                        csrfService,
                        contextProtector,
                        securityAuditEventPort,
                        clock);
        return new SlidingForgotPasswordProtectedAuthFlowContextLoader(
                delegate,
                expiryRefreshPort,
                clock);
    }
}