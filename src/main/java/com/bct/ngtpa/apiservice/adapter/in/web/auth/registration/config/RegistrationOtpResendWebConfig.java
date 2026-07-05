package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationOtpResendWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.workflow.WorkflowMutationResponseMapper;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationIdempotencyPort;
import com.bct.ngtpa.apiservice.application.service.auth.idempotency.RegistrationRequestHashGenerator;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextService;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.RegistrationMessageGateway;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.RegistrationOtpGateway;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.RegistrationSessionGateway;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.ResendRegistrationOtpService;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.ResendRegistrationOtpUseCase;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RegistrationOtpResendWebConfig {

    @Bean
    @ConditionalOnMissingBean
    public RegistrationOtpResendWebMapper registrationOtpResendWebMapper(
            WorkflowMutationResponseMapper workflowMutationResponseMapper) {
        return new RegistrationOtpResendWebMapper(workflowMutationResponseMapper);
    }

    @Bean
    @ConditionalOnMissingBean(ResendRegistrationOtpUseCase.class)
    public ResendRegistrationOtpUseCase resendRegistrationOtpUseCase(
            RegistrationProtectedAuthFlowContextService protectedContextService,
            RegistrationRequestHashGenerator requestHashGenerator,
            RegistrationIdempotencyPort registrationIdempotencyPort,
            RegistrationOtpGateway registrationOtpGateway,
            RegistrationSessionGateway registrationSessionGateway,
            RegistrationMessageGateway registrationMessageGateway,
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
