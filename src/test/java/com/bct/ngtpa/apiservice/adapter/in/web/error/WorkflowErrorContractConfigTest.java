package com.bct.ngtpa.apiservice.adapter.in.web.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

class WorkflowErrorContractConfigTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withInitializer(context -> {
                try {
                    new YamlPropertySourceLoader().load("registration-errors",
                            new ClassPathResource("application-registration-errors.yml"))
                            .forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                } catch (java.io.IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });

    @Test
    void everyTypedKeyHasCompleteYamlDefinition() {
        runner.run(context -> {
            var properties = context.getBean(WorkflowErrorContractProperties.class);
            WorkflowErrorContractValidator.validate(properties);
            for (var key : RegistrationErrorKey.values())
                assertThat(properties.definitionsByCode()).containsKey(key.value());
            for (var key : SharedWorkflowErrorKey.values())
                assertThat(properties.definitionsByCode()).containsKey(key.value());
        });
    }

    @Test
    void resolvesMessageTargetsNextStepAndPageRefFromYaml() {
        runner.run(context -> {
            var resolver = new WorkflowErrorContractResolver(context.getBean(WorkflowErrorContractProperties.class));
            var resolved = resolver.resolve(RegistrationErrorKey.USERNAME_ALREADY_REGISTERED.value(), "zh-HK");
            assertThat(resolved.targets()).containsExactly("username");
            assertThat(resolved.nextStep().name()).isEqualTo("USERNAME_CHECK");
        });
    }

    @Test
    void addingUnusedForgotPasswordNextStepsDoesNotRequireFakeLegacyNavigationEntries() {
        runner.run(context -> {
            var properties = context.getBean(WorkflowErrorContractProperties.class);

            assertThat(properties.getNavigation())
                    .doesNotContainKeys(
                            WorkflowNextStep.ENTER_USERNAME,
                            WorkflowNextStep.ENTER_ID,
                            WorkflowNextStep.ENTER_DETAILS,
                            WorkflowNextStep.PASSWORD_RESET_SUCCESS);
            WorkflowErrorContractValidator.validate(properties);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(WorkflowErrorContractProperties.class)
    static class TestConfig {
    }

    @Test
    void rejectsDuplicatePublicErrorCodes() {
        var properties = new WorkflowErrorContractProperties();
        var first = new WorkflowErrorContractProperties.Definition();
        first.setCode("err.registration.csrf.invalid");
        var second = new WorkflowErrorContractProperties.Definition();
        second.setCode("err.registration.csrf.invalid");
        properties.setErrors(java.util.List.of(first, second));

        assertThatThrownBy(properties::definitionsByCode)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Duplicate workflow error definition: err.registration.csrf.invalid");
    }
}
