package com.bct.ngtpa.apiservice.config.captcha;

import com.bct.ngtpa.apiservice.application.port.in.CreateCaptchaChallengeUseCase;
import com.bct.ngtpa.apiservice.application.port.in.RefreshCaptchaUseCase;
import com.bct.ngtpa.apiservice.application.port.in.VerifyCaptchaUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaAnswerHasherPort;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaChallengePort;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaImageGeneratorPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.captcha.CaptchaChallengeIdGenerator;
import com.bct.ngtpa.apiservice.application.usecase.auth.captcha.CreateCaptchaChallengeService;
import com.bct.ngtpa.apiservice.application.usecase.auth.captcha.RefreshCaptchaService;
import com.bct.ngtpa.apiservice.application.usecase.auth.captcha.SecureCaptchaChallengeIdGenerator;
import com.bct.ngtpa.apiservice.application.usecase.auth.captcha.VerifyCaptchaService;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CaptchaChallengeUseCaseConfig {

    @Bean
    @ConditionalOnMissingBean
    public CaptchaChallengeIdGenerator captchaChallengeIdGenerator() {
        return new SecureCaptchaChallengeIdGenerator();
    }

    @Bean
    @ConditionalOnMissingBean(CreateCaptchaChallengeUseCase.class)
    public CreateCaptchaChallengeUseCase createCaptchaChallengeUseCase(
            AuthWorkflowPolicyPort authWorkflowPolicyPort,
            CaptchaImageGeneratorPort captchaImageGeneratorPort,
            CaptchaAnswerHasherPort captchaAnswerHasherPort,
            CaptchaChallengePort captchaChallengePort,
            CaptchaChallengeIdGenerator captchaChallengeIdGenerator,
            Clock clock) {
        return new CreateCaptchaChallengeService(
                authWorkflowPolicyPort,
                captchaImageGeneratorPort,
                captchaAnswerHasherPort,
                captchaChallengePort,
                captchaChallengeIdGenerator,
                clock);
    }

    @Bean
    @ConditionalOnMissingBean(VerifyCaptchaUseCase.class)
    public VerifyCaptchaUseCase verifyCaptchaUseCase(
            AuthWorkflowPolicyPort authWorkflowPolicyPort,
            CaptchaAnswerHasherPort captchaAnswerHasherPort,
            CaptchaChallengePort captchaChallengePort,
            Clock clock) {
        return new VerifyCaptchaService(
                authWorkflowPolicyPort,
                captchaAnswerHasherPort,
                captchaChallengePort,
                clock);
    }

    @Bean
    @ConditionalOnMissingBean(RefreshCaptchaUseCase.class)
    public RefreshCaptchaUseCase refreshCaptchaUseCase(
            AuthWorkflowPolicyPort authWorkflowPolicyPort,
            CaptchaImageGeneratorPort captchaImageGeneratorPort,
            CaptchaAnswerHasherPort captchaAnswerHasherPort,
            CaptchaChallengePort captchaChallengePort,
            CaptchaChallengeIdGenerator captchaChallengeIdGenerator,
            Clock clock) {
        return new RefreshCaptchaService(
                authWorkflowPolicyPort,
                captchaImageGeneratorPort,
                captchaAnswerHasherPort,
                captchaChallengePort,
                captchaChallengeIdGenerator,
                clock);
    }
}