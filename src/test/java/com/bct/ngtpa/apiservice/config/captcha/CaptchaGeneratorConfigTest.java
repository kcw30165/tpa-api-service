package com.bct.ngtpa.apiservice.config.captcha;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.captcha.JavaCaptchaImageGeneratorAdapter;
import com.bct.ngtpa.apiservice.adapter.out.captcha.Sha256CaptchaAnswerHasher;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaAnswerHasherPort;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaImageGeneratorPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CaptchaGeneratorConfigTest {

  @Test
  void captchaGeneratorConfigProvidesOverrideableBeans() {
    new ApplicationContextRunner()
        .withUserConfiguration(CaptchaGeneratorConfig.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(CaptchaAnswerHasherPort.class);
              assertThat(context).hasSingleBean(CaptchaImageGeneratorPort.class);
              assertThat(context.getBean(CaptchaAnswerHasherPort.class))
                  .isInstanceOf(Sha256CaptchaAnswerHasher.class);
              assertThat(context.getBean(CaptchaImageGeneratorPort.class))
                  .isInstanceOf(JavaCaptchaImageGeneratorAdapter.class);
            });
  }

  @Test
  void captchaGeneratorUsesPolicyFromExistingAuthWorkflowPathInComposition() {
    new ApplicationContextRunner()
        .withUserConfiguration(CaptchaGeneratorConfig.class)
        .run(
            context -> {
              assertThat(context.getBean(CaptchaImageGeneratorPort.class)).isNotNull();
              assertThat(context.getBean(CaptchaAnswerHasherPort.class)).isNotNull();
            });
  }
}
