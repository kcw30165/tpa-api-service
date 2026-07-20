package com.bct.ngtpa.apiservice.config.captcha;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.mapper.CaptchaWebMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CaptchaWebConfig {

    @Bean
    @ConditionalOnMissingBean
    public CaptchaWebMapper captchaWebMapper() {
        return new CaptchaWebMapper();
    }
}