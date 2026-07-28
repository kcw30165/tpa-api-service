package com.bct.ngtpa.apiservice.config.auth.login;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.mapper.LoginOtpSendWebMapper;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.LoginOtpSendPolicy;
import com.bct.ngtpa.apiservice.application.port.in.SendLoginOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.service.security.ratelimit.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.*;
import java.time.*;
import org.springframework.context.annotation.*;

@Configuration
public class LoginOtpSendApplicationConfig {
    @Bean
    LoginOtpSendWebMapper loginOtpSendWebMapper() {
        return new LoginOtpSendWebMapper();
    }

    @Bean
    DestinationBucketIdGenerator loginOtpDestinationBucketIdGenerator() {
        return new Sha256DestinationBucketIdGenerator();
    }

    @Bean
    SendLoginOtpUseCase sendLoginOtpUseCase(LoginProtectedAuthFlowContextLoader loader, OtpChallengePort otp,
            MessageDeliveryPort messages, LoginOtpSessionBindingPort sessions, DestinationBucketIdGenerator buckets,
            LoginOtpSendIdempotencyPort idempotency, Clock clock, AuthWorkflowProperties properties) {
        properties.validateLoginPolicy();
        var p = properties.getLogin().getOtp();
        var policy = new LoginOtpSendPolicy(p.getLength(), p.isNumericOnly(), p.getTtlSeconds(),
                p.getResendCooldownSeconds(), p.getMaxVerifyAttempts(), p.isResendLimitEnabled(),
                p.getMaxResendAttempts(), p.getSmsTemplate(), p.getEmailTemplate().getSubject());
        var core = new LoginOtpSendService(loader, new LoginOtpDestinationResolver(), otp, messages, sessions, buckets,
                clock, policy);
        return new IdempotentLoginOtpSendService(core, idempotency, new LoginOtpSendRequestHashGenerator(), clock,
                Duration.ofSeconds(properties.getLogin().getIdempotency().getOtpSendTtlSeconds()));
    }
}
