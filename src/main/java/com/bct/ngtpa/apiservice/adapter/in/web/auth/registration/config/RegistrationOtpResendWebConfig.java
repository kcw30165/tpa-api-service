package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config;

import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationOtpResendWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.workflow.WorkflowMutationResponseMapper;
import com.bct.ngtpa.apiservice.application.port.in.ResendRegistrationOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationIdempotencyPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationMessagePort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationOtpPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationSessionPort;
import com.bct.ngtpa.apiservice.application.service.auth.idempotency.RegistrationRequestHashGenerator;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextLoader;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.ResendRegistrationOtpService;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RegistrationOtpResendWebConfig {

    @Bean
    @ConditionalOnMissingBean
    public RegistrationOtpResendWebMapper registrationOtpResendWebMapper(
            WorkflowMutationResponseMapper workflowMutationResponseMapper,
            ErrorMessageResolver errorMessageResolver) {
        return new RegistrationOtpResendWebMapper(
                workflowMutationResponseMapper,
                errorMessageResolver);
    }

    @Bean
    @ConditionalOnMissingBean(ResendRegistrationOtpUseCase.class)
    public ResendRegistrationOtpUseCase resendRegistrationOtpUseCase(
            RegistrationProtectedAuthFlowContextLoader protectedContextService,
            RegistrationRequestHashGenerator requestHashGenerator,
            RegistrationIdempotencyPort registrationIdempotencyPort,
            RegistrationOtpPort registrationOtpGateway,
            RegistrationSessionPort registrationSessionGateway,
            RegistrationMessagePort registrationMessageGateway,
            Clock clock) {
        return new ResendRegistrationOtpService(
                protectedContextService,
                requestHashGenerator,
                registrationIdempotencyPort,
                registrationOtpGateway,
                registrationSessionGateway,
                registrationMessageGateway,
                clock);
    }
}
