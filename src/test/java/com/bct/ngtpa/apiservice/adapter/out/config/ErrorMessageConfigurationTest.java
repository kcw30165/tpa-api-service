package com.bct.ngtpa.apiservice.adapter.out.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ErrorMessageConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(context -> {
                try {
                    var propertySources = new YamlPropertySourceLoader()
                            .load("applicationYml", new ClassPathResource("application.yml"));
                    propertySources.forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                } catch (java.io.IOException ex) {
                    throw new IllegalStateException("Failed to load application.yml", ex);
                }
            });

    @Test
    void loadsBaseEnglishAndChineseErrorMessages() {
        contextRunner.run(context -> {
            var environment = context.getEnvironment();

            assertEquals(
                    "Invalid request payload.",
                    environment.getProperty("error-message.en.err.request.validation.failed"));
            assertEquals(
                    "請求內容無效。",
                    environment.getProperty("error-message.zh_HK.err.request.validation.failed"));
        });
    }

    @Test
    void loadsQuotedDottedAndVariantErrorMessageKeys() {
        contextRunner.run(context -> {
            var environment = context.getEnvironment();

            assertEquals(
                    "Service is temporarily unavailable. Please try again later.",
                    environment.getProperty("error-message.en.err.apim.service.unavailable"));
            assertEquals(
                    "Service is temporarily unavailable in JP environment. Please try again later.",
                    environment.getProperty("error-message.en.err.apim.service.unavailable.JP"));
            assertEquals(
                    "Member context is unavailable for this scheme. Please try again later.",
                    environment.getProperty("error-message.en.err.member.context.unavailable.JP.JPM.OE"));
        });
    }
}