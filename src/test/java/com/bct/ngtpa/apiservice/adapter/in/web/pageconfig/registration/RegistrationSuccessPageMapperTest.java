package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.registration;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.response.RegistrationSuccessPageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegistrationSuccessPageMapperTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withInitializer(context -> {
                var resource = new ClassPathResource("application-page-registration.yml");
                try {
                    var propertySources = new YamlPropertySourceLoader().load("registrationPage", resource);
                    propertySources.forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                } catch (java.io.IOException ex) {
                    throw new IllegalStateException("Failed to load registration page YAML", ex);
                }
            });

    @Test
    void mapsNoRemainingTrustsSuccessPageFromBffPages() {
        contextRunner.run(context -> {
            RegistrationSuccessPageResponse response = mapper(context.getBean(BffPagesProperties.class))
                    .map("registration-success", "noRemainingTrusts", "en");

            assertEquals("registration-success", response.pageId());
            assertEquals("noRemainingTrusts", response.variant());
            assertEquals("Successful Registration", response.title());
            assertFalse(response.body().isBlank());
            assertEquals(1, response.actions().size());
            assertEquals("returnToLogin", response.actions().get(0).id());
            assertEquals("PRIMARY", response.actions().get(0).type());
            assertEquals("Return to Login", response.actions().get(0).label());
            assertEquals("RETURN_TO_LOGIN", response.actions().get(0).nextStep());
        });
    }

    @Test
    void mapsHasRemainingTrustsSuccessPageFromBffPages() {
        contextRunner.run(context -> {
            RegistrationSuccessPageResponse response = mapper(context.getBean(BffPagesProperties.class))
                    .map("registration-success", "hasRemainingTrusts", "en");

            assertEquals("registration-success", response.pageId());
            assertEquals("hasRemainingTrusts", response.variant());
            assertEquals("Successful Registration", response.title());
            assertFalse(response.body().isBlank());
            assertEquals(2, response.actions().size());
            assertEquals("continueRegistration", response.actions().get(0).id());
            assertEquals("PRIMARY", response.actions().get(0).type());
            assertEquals("Continue Registration for ORSO Schemes", response.actions().get(0).label());
            assertEquals("SELECT_TRUST", response.actions().get(0).nextStep());
            assertEquals("returnToLogin", response.actions().get(1).id());
            assertEquals("SECONDARY", response.actions().get(1).type());
            assertEquals("Return to Login", response.actions().get(1).label());
            assertEquals("RETURN_TO_LOGIN", response.actions().get(1).nextStep());
        });
    }

    @Test
    void resolvesChineseLabelsWhenLocaleIsZhHk() {
        contextRunner.run(context -> {
            RegistrationSuccessPageResponse response = mapper(context.getBean(BffPagesProperties.class))
                    .map("registration-success", "hasRemainingTrusts", "zh-HK");

            assertEquals("註冊成功", response.title());
            assertEquals("繼續為其他公積金計劃註冊", response.actions().get(0).label());
            assertEquals("返回登入頁面", response.actions().get(1).label());
        });
    }

    @Test
    void fallsBackToEnglishForUnsupportedLocale() {
        contextRunner.run(context -> {
            RegistrationSuccessPageResponse response = mapper(context.getBean(BffPagesProperties.class))
                    .map("registration-success", "noRemainingTrusts", "fr");

            assertEquals("Successful Registration", response.title());
            assertEquals("Return to Login", response.actions().get(0).label());
        });
    }

    @Test
    void rejectsUnknownRegistrationSuccessVariant() {
        contextRunner.run(context -> {
            var ex = assertThrows(IllegalStateException.class,
                    () -> mapper(context.getBean(BffPagesProperties.class))
                            .map("registration-success", "unknownVariant", "en"));
            assertEquals("Registration success page variant is missing: unknownVariant", ex.getMessage());
        });
    }

    @Test
    void rejectsMissingRegistrationSuccessPage() {
        BffPagesProperties properties = new BffPagesProperties();
        properties.setPages(new LinkedHashMap<>(Map.of()));

        var ex = assertThrows(IllegalStateException.class,
                () -> mapper(properties).map("registration-success", "noRemainingTrusts", "en"));
        assertEquals("Registration success page configuration is missing.", ex.getMessage());
    }

    @Test
    void mapperDoesNotUseValueByVariant() {
        contextRunner.run(context -> {
            RegistrationSuccessPageResponse response = mapper(context.getBean(BffPagesProperties.class))
                    .map("registration-success", "noRemainingTrusts", "en");

            assertEquals("registration-success", response.pageId());
            assertEquals("noRemainingTrusts", response.variant());
        });
    }

    @Test
    void noApplicationPortOrApplicationDtoCreated() throws Exception {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.bct.ngtpa.apiservice.application.port.out.RegistrationSuccessPageResolverPort"));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.bct.ngtpa.apiservice.application.dto.RegistrationSuccessPageResponse"));
    }

    private RegistrationSuccessPageMapper mapper(BffPagesProperties properties) {
        return new RegistrationSuccessPageMapper(properties);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(BffPagesProperties.class)
    static class TestConfig {
    }
}