package com.bct.ngtpa.apiservice.config.captcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bct.ngtpa.apiservice.application.port.in.CreateCaptchaChallengeUseCase;
import com.bct.ngtpa.apiservice.application.port.in.RefreshCaptchaUseCase;
import com.bct.ngtpa.apiservice.application.port.in.VerifyCaptchaUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaAnswerHasherPort;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaChallengePort;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaImageGeneratorPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.captcha.CaptchaChallengeIdGenerator;
import com.bct.ngtpa.apiservice.application.usecase.auth.captcha.SecureCaptchaChallengeIdGenerator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CaptchaChallengeUseCaseConfigTest {

    @Test
    void captchaChallengeUseCaseConfigProvidesOverrideableBeans() {
        new ApplicationContextRunner()
                .withUserConfiguration(CaptchaChallengeUseCaseConfig.class)
                .withBean(AuthWorkflowPolicyPort.class, () -> mock(AuthWorkflowPolicyPort.class))
                .withBean(CaptchaImageGeneratorPort.class, () -> mock(CaptchaImageGeneratorPort.class))
                .withBean(CaptchaAnswerHasherPort.class, () -> mock(CaptchaAnswerHasherPort.class))
                .withBean(CaptchaChallengePort.class, () -> mock(CaptchaChallengePort.class))
                .withBean(Clock.class, () -> Clock.fixed(Instant.parse("2026-07-07T12:00:00Z"), ZoneOffset.UTC))
                .run(context -> {
                    assertThat(context).hasSingleBean(CaptchaChallengeIdGenerator.class);
                    assertThat(context).hasSingleBean(CreateCaptchaChallengeUseCase.class);
                    assertThat(context).hasSingleBean(VerifyCaptchaUseCase.class);
                    assertThat(context).hasSingleBean(RefreshCaptchaUseCase.class);
                    assertThat(context.getBean(CaptchaChallengeIdGenerator.class))
                            .isInstanceOf(SecureCaptchaChallengeIdGenerator.class);
                });
    }

    @Test
    void captchaUseCaseBeansUseExistingPolicyPort() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice/config/captcha/CaptchaChallengeUseCaseConfig.java"));

        assertThat(source)
                .contains("AuthWorkflowPolicyPort")
                .doesNotContain("AuthWorkflowProperties");
    }
}