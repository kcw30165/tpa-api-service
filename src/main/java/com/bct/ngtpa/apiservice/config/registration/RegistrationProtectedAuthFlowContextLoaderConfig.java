package com.bct.ngtpa.apiservice.config.registration;

import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionExpiryRefreshPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.DefaultRegistrationProtectedAuthFlowContextLoader;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextLoader;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.SlidingRegistrationProtectedAuthFlowContextLoader;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionCsrfService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionLifecycleService;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RegistrationProtectedAuthFlowContextLoaderConfig {

    @Bean
    @ConditionalOnMissingBean(RegistrationProtectedAuthFlowContextLoader.class)
    public RegistrationProtectedAuthFlowContextLoader registrationProtectedAuthFlowContextLoader(
            AuthFlowSessionLifecycleService lifecycleService,
            AuthFlowSessionCsrfService csrfService,
            RegistrationAuthFlowContextProtectorPort contextProtector,
            AuthFlowSessionExpiryRefreshPort expiryRefreshPort,
            Clock clock) {
        RegistrationProtectedAuthFlowContextLoader delegate =
                new DefaultRegistrationProtectedAuthFlowContextLoader(
                        lifecycleService,
                        csrfService,
                        contextProtector,
                        clock);
        return new SlidingRegistrationProtectedAuthFlowContextLoader(
                delegate,
                expiryRefreshPort,
                clock);
    }
}
