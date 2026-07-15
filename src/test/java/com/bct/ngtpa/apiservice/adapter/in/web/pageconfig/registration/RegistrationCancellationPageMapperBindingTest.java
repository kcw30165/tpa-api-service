package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PageDisplayTextResolver;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.shared.config.ConfigVariantCandidateGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

class RegistrationCancellationPageMapperBindingTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withInitializer(context -> {
                var resource = new ClassPathResource("application-page-registration.yml");
                try {
                    var sources = new YamlPropertySourceLoader().load("registrationPage", resource);
                    sources.forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                } catch (java.io.IOException exception) {
                    throw new IllegalStateException("Failed to load registration page YAML", exception);
                }
            });

    @Test
    void bindsStandaloneCancellationPageWithoutVariant() {
        contextRunner.run(context -> {
            var page = context.getBean(BffPagesProperties.class)
                    .getPages().get("registration-cancelled");
            assertNotNull(page);
            assertNotNull(page.getMetadata());
            assertEquals("registration-cancelled", page.getMetadata().getId());
            assertEquals("registration.cancelled.title", page.getMetadata().getTitleCode());
            assertNotNull(page.getDisplay());
            assertFalse(page.getDisplay().isEmpty());
            assertNotNull(page.getForm());
            assertNotNull(page.getForm().getActions());
            assertFalse(page.getForm().getActions().isEmpty());
            assertNotNull(page.getVariants());
            assertTrueEmpty(page.getVariants());
        });
    }

    @Test
    void mapsEnglishCancellationPageFromExistingDisplayProperty() {
        contextRunner.run(context -> {
            var response = mapper(context.getBean(BffPagesProperties.class)).map("en");
            assertEquals("registration-cancelled", response.pageId());
            assertEquals("Registration Cancelled", response.title());
            assertEquals(
                    "Your registration has been cancelled. Please return to the login page.",
                    response.body());
            assertEquals(1, response.actions().size());
            assertEquals("returnToLogin", response.actions().get(0).id());
            assertEquals("PRIMARY", response.actions().get(0).type());
            assertEquals("Return to Login", response.actions().get(0).label());
            assertEquals("RETURN_TO_LOGIN", response.actions().get(0).nextStep());
        });
    }

    @Test
    void mapsTraditionalChineseCancellationPageFromExistingDisplayProperty() {
        contextRunner.run(context -> {
            var response = mapper(context.getBean(BffPagesProperties.class)).map("zh-HK");
            assertEquals("取消註冊", response.title());
            assertEquals("你的註冊程序已取消。請返回登入頁面。", response.body());
            assertEquals("返回登入頁面", response.actions().get(0).label());
        });
    }

    private RegistrationCancellationPageMapper mapper(BffPagesProperties properties) {
        return new RegistrationCancellationPageMapper(
                properties,
                new PageDisplayTextResolver(new ConfigVariantCandidateGenerator()));
    }

    private void assertTrueEmpty(java.util.Map<?, ?> values) {
        assertEquals(0, values.size());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(BffPagesProperties.class)
    static class TestConfig {
    }
}
