package com.bct.ngtpa.apiservice.config.captcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.mapper.CaptchaWebMapper;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CaptchaWebConfigTest {

    @Test
    void captchaWebMapperBeanIsRegistered() {
        new ApplicationContextRunner()
                .withUserConfiguration(CaptchaWebConfig.class)
                .withBean(ErrorMessageResolver.class, () -> mock(ErrorMessageResolver.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(CaptchaWebMapper.class);
                });
    }
}