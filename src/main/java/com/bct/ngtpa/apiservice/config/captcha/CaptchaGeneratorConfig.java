package com.bct.ngtpa.apiservice.config.captcha;

import com.bct.ngtpa.apiservice.adapter.out.captcha.JavaCaptchaImageGeneratorAdapter;
import com.bct.ngtpa.apiservice.adapter.out.captcha.SecureRandomCaptchaTextGenerator;
import com.bct.ngtpa.apiservice.adapter.out.captcha.Sha256CaptchaAnswerHasher;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaAnswerHasherPort;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaImageGeneratorPort;
import java.security.SecureRandom;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CaptchaGeneratorConfig {

    @Bean
    @ConditionalOnMissingBean
    public SecureRandom captchaSecureRandom() {
        return new SecureRandom();
    }

    @Bean
    @ConditionalOnMissingBean
    public SecureRandomCaptchaTextGenerator secureRandomCaptchaTextGenerator(SecureRandom captchaSecureRandom) {
        return new SecureRandomCaptchaTextGenerator(captchaSecureRandom);
    }

    @Bean
    @ConditionalOnMissingBean
    public CaptchaAnswerHasherPort captchaAnswerHasherPort() {
        return new Sha256CaptchaAnswerHasher();
    }

    @Bean
    @ConditionalOnMissingBean
    public CaptchaImageGeneratorPort captchaImageGeneratorPort(
            SecureRandomCaptchaTextGenerator secureRandomCaptchaTextGenerator,
            SecureRandom captchaSecureRandom) {
        return new JavaCaptchaImageGeneratorAdapter(secureRandomCaptchaTextGenerator, captchaSecureRandom);
    }
}