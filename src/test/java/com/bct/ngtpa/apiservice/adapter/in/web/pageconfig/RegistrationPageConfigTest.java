package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistrationPageConfigTest {

    private static final Path REGISTRATION_YAML = Path.of("src/main/resources/application-page-registration.yml");

    private final ApplicationContextRunner registrationOnlyContextRunner = contextRunner(
            "application-page-registration.yml");

    private final ApplicationContextRunner combinedContextRunner = contextRunner(
            "application-page-personal-information.yml",
            "application-page-registration.yml");

    @Test
    void registrationPagesBindFromYaml() {
        registrationOnlyContextRunner.run(context -> {
            var pages = context.getBean(BffPagesProperties.class).getPages();

            assertTrue(pages.containsKey("registration-start"));
            assertTrue(pages.containsKey("registration-otp-verification"));
            assertTrue(pages.containsKey("registration-trust-selection"));
            assertTrue(pages.containsKey("registration-username-check"));
            assertTrue(pages.containsKey("registration-password-setup"));
            assertTrue(pages.containsKey("registration-cancelled"));
            assertTrue(pages.containsKey("registration-success"));
        });
    }

    @Test
    void registrationSuccessPageBindsPageLevelVariants() {
        registrationOnlyContextRunner.run(context -> {
            var successPage = context.getBean(BffPagesProperties.class).getPages().get("registration-success");

            assertNotNull(successPage.getVariants());
            assertTrue(successPage.getVariants().containsKey("noRemainingTrusts"));
            assertTrue(successPage.getVariants().containsKey("hasRemainingTrusts"));
        });
    }

    @Test
    void registrationSuccessPageDefinesNoRemainingTrustsVariant() {
        registrationOnlyContextRunner.run(context -> {
            var variant = context.getBean(BffPagesProperties.class)
                    .getPages()
                    .get("registration-success")
                    .getVariants()
                    .get("noRemainingTrusts");

            assertEquals("Successful Registration", variant.getTitle().get("en"));
            assertFalse(variant.getBody().get("en").isBlank());
            assertEquals(1, variant.getActions().size());
            assertEquals("returnToLogin", variant.getActions().get(0).getId());
            assertEquals("PRIMARY", variant.getActions().get(0).getType());
            assertEquals("RETURN_TO_LOGIN", variant.getActions().get(0).getNextStep());
        });
    }

    @Test
    void registrationSuccessPageDefinesHasRemainingTrustsVariant() {
        registrationOnlyContextRunner.run(context -> {
            var variant = context.getBean(BffPagesProperties.class)
                    .getPages()
                    .get("registration-success")
                    .getVariants()
                    .get("hasRemainingTrusts");

            assertEquals("Successful Registration", variant.getTitle().get("en"));
            assertFalse(variant.getBody().get("en").isBlank());
            assertEquals(List.of("continueRegistration", "returnToLogin"),
                    variant.getActions().stream().map(ActionProperties::getId).toList());
            assertEquals("PRIMARY", variant.getActions().get(0).getType());
            assertEquals("SELECT_TRUST", variant.getActions().get(0).getNextStep());
            assertEquals("SECONDARY", variant.getActions().get(1).getType());
            assertEquals("RETURN_TO_LOGIN", variant.getActions().get(1).getNextStep());
        });
    }

    @Test
    void registrationPagesPassPageSchemaLintWithVariants() {
        registrationOnlyContextRunner.run(context -> {
            var result = PageSchemaLinter.lint(context.getBean(BffPagesProperties.class));
            assertTrue(result.getErrors().isEmpty(), "Expected no lint errors but got: " + result.getErrors());
        });
    }

    @Test
    void existingPageConfigStillBindsAndLints() {
        combinedContextRunner.run(context -> {
            var properties = context.getBean(BffPagesProperties.class);
            assertTrue(properties.getPages().containsKey("personalInformation"));
            assertTrue(properties.getPages().containsKey("registration-success"));
            assertTrue(PageSchemaLinter.lint(properties).getErrors().isEmpty());
        });
    }

    @Test
    void registrationSuccessPageDoesNotUseValueByVariantForPageVariants() throws Exception {
        String yaml = Files.readString(REGISTRATION_YAML);

        assertTrue(yaml.contains("registration-success:"));
        assertTrue(yaml.contains("variants:"));
        assertFalse(yaml.contains("valueByVariant"));
    }

    @Test
    void registrationSuccessPageDoesNotRequireHardcodedUseCaseText() {
        registrationOnlyContextRunner.run(context -> {
            var successPage = context.getBean(BffPagesProperties.class).getPages().get("registration-success");
            var noRemainingTrusts = successPage.getVariants().get("noRemainingTrusts");
            var hasRemainingTrusts = successPage.getVariants().get("hasRemainingTrusts");

            assertEquals("Successful Registration", noRemainingTrusts.getTitle().get("en"));
            assertFalse(noRemainingTrusts.getBody().get("en").isBlank());
            assertFalse(noRemainingTrusts.getActions().isEmpty());

            assertEquals("Successful Registration", hasRemainingTrusts.getTitle().get("en"));
            assertFalse(hasRemainingTrusts.getBody().get("en").isBlank());
            assertFalse(hasRemainingTrusts.getActions().isEmpty());
        });
    }

    @Test
    void registrationTrustSelectionPageDoesNotDefineSubmitActions() {
        registrationOnlyContextRunner.run(context -> {
            var trustSelectionPage = context.getBean(BffPagesProperties.class).getPages().get("registration-trust-selection");

            assertNotNull(trustSelectionPage);
            var actions = trustSelectionPage.getForm() == null ? List.<ActionProperties>of() : trustSelectionPage.getForm().getActions();
            List<String> actionNames = actions == null ? List.of() : actions.stream()
                    .map(action -> action.getName() != null ? action.getName() : action.getId())
                    .toList();

            assertFalse(actionNames.contains("continue"));
            assertFalse(actionNames.contains("cancelRegistration"));
        });
    }

    private ApplicationContextRunner contextRunner(String... resourceNames) {
        return new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class)
                .withInitializer(context -> {
                    var loader = new YamlPropertySourceLoader();
                    for (String resourceName : resourceNames) {
                        var resource = new ClassPathResource(resourceName);
                        try {
                            var propertySources = loader.load(resourceName, resource);
                            propertySources.forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                        } catch (java.io.IOException ex) {
                            throw new IllegalStateException("Failed to load YAML " + resourceName, ex);
                        }
                    }
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(BffPagesProperties.class)
    static class TestConfig {
    }
}