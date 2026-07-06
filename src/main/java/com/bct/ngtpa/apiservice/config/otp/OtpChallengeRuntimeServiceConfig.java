package com.bct.ngtpa.apiservice.config.otp;

import com.bct.ngtpa.apiservice.application.port.out.OtpChallengePort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengeStatePort;
import com.bct.ngtpa.apiservice.application.usecase.auth.otp.DefaultOtpChallengeAdapter;
import com.bct.ngtpa.apiservice.application.service.auth.otp.OtpChallengeIdGenerator;
import com.bct.ngtpa.apiservice.application.service.auth.otp.OtpCodeGenerator;
import com.bct.ngtpa.apiservice.application.service.auth.otp.OtpCodeHasher;
import com.bct.ngtpa.apiservice.application.service.auth.otp.SecureOtpChallengeIdGenerator;
import com.bct.ngtpa.apiservice.application.service.auth.otp.SecureOtpCodeGenerator;
import com.bct.ngtpa.apiservice.application.service.auth.otp.Sha256OtpCodeHasher;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OtpChallengeRuntimeServiceConfig {

    @Bean
    @ConditionalOnMissingBean
    public OtpCodeGenerator otpCodeGenerator() {
        return new SecureOtpCodeGenerator();
    }

    @Bean
    @ConditionalOnMissingBean
    public OtpCodeHasher otpCodeHasher() {
        return new Sha256OtpCodeHasher();
    }

    @Bean
    @ConditionalOnMissingBean
    public OtpChallengeIdGenerator otpChallengeIdGenerator() {
        return new SecureOtpChallengeIdGenerator();
    }

    @Bean
    @ConditionalOnMissingBean(OtpChallengePort.class)
    public OtpChallengePort otpChallengePort(
            OtpChallengeStatePort otpChallengeStatePort,
            OtpCodeGenerator otpCodeGenerator,
            OtpCodeHasher otpCodeHasher,
            OtpChallengeIdGenerator otpChallengeIdGenerator,
            Clock clock) {
        return new DefaultOtpChallengeAdapter(
                otpChallengeStatePort,
                otpCodeGenerator,
                otpCodeHasher,
                otpChallengeIdGenerator,
                clock);
    }
}