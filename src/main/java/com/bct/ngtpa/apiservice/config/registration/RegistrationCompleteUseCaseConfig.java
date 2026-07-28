package com.bct.ngtpa.apiservice.config.registration;

import com.bct.ngtpa.apiservice.adapter.in.web.session.config.AuthFlowSessionProperties;
import com.bct.ngtpa.apiservice.application.port.in.CompleteRegistrationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.service.auth.idempotency.RegistrationRequestHashGenerator;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextLoader;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.*;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RegistrationCompleteUseCaseConfig {
    @Bean
    @ConditionalOnMissingBean(RegistrationPasswordValidator.class)
    RegistrationPasswordValidator registrationPasswordValidator(AuthWorkflowPolicyPort policy) {
        return new RegistrationPasswordValidator(policy);
    }

    @Bean
    @ConditionalOnMissingBean(RegistrationUsernameCheckContextLoader.class)
    RegistrationUsernameCheckContextLoader registrationUsernameCheckContextLoader(
            RegistrationUsernameCheckProtectorPort protector) {
        return new RegistrationUsernameCheckContextLoader(protector);
    }

    @Bean
    @ConditionalOnMissingBean(CompleteRegistrationUseCase.class)
    CompleteRegistrationUseCase completeRegistrationUseCase(
            RegistrationProtectedAuthFlowContextLoader contextLoader,
            AuthFlowSessionPort sessions,
            RegistrationUsernameCheckContextLoader usernameLoader,
            RegistrationPasswordValidator passwordValidator,
            RegistrationIdempotencyPort idempotency,
            RegistrationRequestHashGenerator hashes,
            AuthFlowSessionCompletionLockPort locks,
            AuthFlowSessionExpiryRefreshPort expiry,
            RegistrationCompletionPort completion,
            RegistrationLookupPort lookup,
            AuthFlowSessionCancellationPort cancellation,
            AuthFlowSessionProperties properties,
            Clock clock) {
        Duration lease = Duration.ofSeconds(properties.getCompletionLock().getLeaseSeconds());
        return new CompleteRegistrationService(contextLoader, sessions, usernameLoader,
                passwordValidator, idempotency, hashes, locks, expiry, completion, lookup,
                cancellation, lease, clock);
    }
}
