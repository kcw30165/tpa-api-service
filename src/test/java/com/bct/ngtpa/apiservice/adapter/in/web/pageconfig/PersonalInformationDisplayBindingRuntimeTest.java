package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PageDisplayTextResolver;
import com.bct.ngtpa.apiservice.shared.config.ConfigVariantCandidateGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

class PersonalInformationDisplayBindingRuntimeTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withInitializer(context -> {
                var resource = new ClassPathResource("application-page-personal-information.yml");
                try {
                    var propertySources = new YamlPropertySourceLoader().load("personalInformationPage", resource);
                    propertySources.forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                } catch (java.io.IOException ex) {
                    throw new IllegalStateException("Failed to load YAML", ex);
                }
            });

    @Test
    void bindsDottedDisplayCodesAsFullMapKeys() {
        contextRunner.run(context -> {
            var bound = context.getBean(BffPagesProperties.class);
            PageSchemaProperties page = bound.getPages().get("personalInformation");

            assertThat(page.getDisplay()).containsKeys(
                    "personalInformation.page.title",
                    "personalInformation.action.submit.label",
                    "personalInformation.field.emailAddress.label",
                    "personalInformation.section.addressInformation.title",
                    "personalInformation.address.poBoxBlocked.message",
                    "personalInformation.address.poBoxBlocked.message.JP",
                    "personalInformation.confirmation.title");

            assertThat(page.getDisplay().get("personalInformation.page.title"))
                    .containsEntry("en", "Personal Information");
        });
    }

    @Test
    void resolverCanResolveLabelsFromSpringBoundRealYamlDisplayMap() {
        contextRunner.run(context -> {
            var bound = context.getBean(BffPagesProperties.class);
            PageSchemaProperties page = bound.getPages().get("personalInformation");
            var resolver = new PageDisplayTextResolver(new ConfigVariantCandidateGenerator());

            assertThat(resolver.resolve(
                    page.getDisplay(),
                    "personalInformation.page.title",
                    null,
                    "en",
                    "DB",
                    null,
                    null))
                    .isEqualTo("Personal Information");

            assertThat(resolver.resolve(
                    page.getDisplay(),
                    "personalInformation.field.emailAddress.label",
                    null,
                    "en",
                    "DB",
                    null,
                    null))
                    .isEqualTo("Email Address");
        });
    }

    @Test
    void accountEnvVariantFallsBackToBaseWhenSpecificVariantIsMissingAndUsesSpecificWhenPresent() {
        contextRunner.run(context -> {
            var bound = context.getBean(BffPagesProperties.class);
            PageSchemaProperties page = bound.getPages().get("personalInformation");
            var resolver = new PageDisplayTextResolver(new ConfigVariantCandidateGenerator());

            assertThat(resolver.resolve(
                    page.getDisplay(),
                    "personalInformation.address.poBoxBlocked.message",
                    null,
                    "en",
                    "DB",
                    null,
                    null))
                    .isEqualTo("PO Box address will not be accepted, please re-enter your address as our record.");

            assertThat(resolver.resolve(
                    page.getDisplay(),
                    "personalInformation.address.poBoxBlocked.message",
                    null,
                    "en",
                    "JP",
                    null,
                    null))
                    .isEqualTo("PO Box address will not be accepted for Japan accounts, please re-enter your address as our record.");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(BffPagesProperties.class)
    static class TestConfig {
    }
}
