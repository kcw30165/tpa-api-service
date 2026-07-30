package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.error.WorkflowErrorContractProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.error.WorkflowErrorContractResolver;
import com.bct.ngtpa.apiservice.adapter.in.web.error.WorkflowErrorContractValidator;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ActionProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PageSchemaLinter;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

class RegistrationDslRuntimeBindingRegressionTest {

    private static final Set<String> REQUIRED_PAGES = Set.of(
            "registration-start",
            "registration-otp-verification",
            "registration-trust-selection",
            "registration-username-check",
            "registration-password-setup",
            "registration-cancelled",
            "registration-success");

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withInitializer(context -> {
                load(context, "application-page-registration.yml");
                load(context, "application-registration-errors.yml");
            });

    @Test
    void allRegistrationPagesBindThroughSpringAndPassSchemaLint() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(BffPagesProperties.class);
            assertThat(properties.getPages().keySet()).containsAll(REQUIRED_PAGES);
            assertThat(PageSchemaLinter.lint(properties).getErrors()).isEmpty();
        });
    }

    @Test
    void interactivePagesBindFormsFieldsAndActionsAtRuntime() {
        runner.run(context -> {
            var pages = context.getBean(BffPagesProperties.class).getPages();
            assertFormAction(pages.get("registration-start"), "submitRegistrationStart");
            assertFormAction(pages.get("registration-otp-verification"), "verifyOtp");
            assertFormAction(pages.get("registration-otp-verification"), "resendOtp");
            assertFormAction(pages.get("registration-username-check"), "checkUsername");
            assertFormAction(pages.get("registration-password-setup"), "completeRegistration");
            assertThat(pages.get("registration-start").getForm().getSections())
                    .flatExtracting(section -> section.getFields())
                    .extracting(field -> field.getId())
                    .contains("identityType", "identityValue", "dateOfBirth",
                            "mobileNumber", "emailAddress", "captchaAnswer");
            assertThat(pages.get("registration-username-check").getForm().getSections())
                    .flatExtracting(section -> section.getFields())
                    .extracting(field -> field.getId())
                    .contains("username");
        });
    }

    @Test
    void cancellationPageMapsLanguageIndependentSemanticContract() {
        runner.run(context -> {
            var mapper = new RegistrationCancellationPageMapper(
                    context.getBean(BffPagesProperties.class));
            var english = mapper.map("en");
            var chinese = mapper.map("zh-HK");

            assertThat(english).isEqualTo(chinese);
            assertThat(english.pageId()).isEqualTo("registration-cancelled");
            assertThat(english.actions()).singleElement().satisfies(action -> {
                assertThat(action.id()).isEqualTo("returnToLogin");
                assertThat(action.type()).isEqualTo("PRIMARY");
                assertThat(action.nextStep()).isEqualTo(WorkflowNextStep.RETURN_TO_LOGIN);
            });
        });
    }

    @Test
    void successPageMapsBothRuntimeVariantsAndConfiguredNavigation() {
        runner.run(context -> {
            var mapper = new RegistrationSuccessPageMapper(
                    context.getBean(BffPagesProperties.class));
            var complete = mapper.map(
                    "registration-success", "noRemainingTrusts", "en");
            var remaining = mapper.map(
                    "registration-success", "hasRemainingTrusts", "zh-HK");

            assertThat(complete.pageId()).isEqualTo("registration-success");
            assertThat(complete.variant()).isEqualTo("noRemainingTrusts");
            assertThat(complete.actions()).singleElement().satisfies(action ->
                    assertThat(action.nextStep()).isEqualTo(WorkflowNextStep.RETURN_TO_LOGIN));
            assertThat(remaining.variant()).isEqualTo("hasRemainingTrusts");
            assertThat(remaining.actions())
                    .extracting(action -> action.nextStep())
                    .containsExactly(WorkflowNextStep.SELECT_TRUST, WorkflowNextStep.RETURN_TO_LOGIN);
        });
    }

    @Test
    void errorCodeResolvesMessageTargetsNextStepAndPageRefFromRuntimeYaml() {
        runner.run(context -> {
            var properties = context.getBean(WorkflowErrorContractProperties.class);
            WorkflowErrorContractValidator.validate(properties);
            var resolver = new WorkflowErrorContractResolver(properties);
            var username = resolver.resolve(
                    RegistrationErrorKey.USERNAME_ALREADY_REGISTERED.value(),
                    "zh-HK");
            assertThat(username.targets()).containsExactly("username");
            assertThat(username.nextStep()).isEqualTo(WorkflowNextStep.USERNAME_CHECK);
            var alreadyRegistered = resolver.resolve(
                    RegistrationErrorKey.COMPLETE_MEMBER_ALREADY_REGISTERED.value(),
                    "en");
            assertThat(alreadyRegistered.nextStep()).isEqualTo(WorkflowNextStep.RETURN_TO_LOGIN);
        });
    }

    @Test
    void downstreamClassificationsBindOnExactlyTheThreeApimBackedPages() {
        runner.run(context -> {
            var pages = context.getBean(BffPagesProperties.class).getPages();
            assertThat(pages.get("registration-start").getDownstreamErrorClassification()
                    .getEndpoint()).isEqualTo("TRPSearchUnregMember");
            assertThat(pages.get("registration-username-check").getDownstreamErrorClassification()
                    .getEndpoint()).isEqualTo("TRPChkCustId");
            assertThat(pages.get("registration-password-setup").getDownstreamErrorClassification()
                    .getEndpoint()).isEqualTo("TRPRegMember");
        });
    }

    private static void assertFormAction(
            com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PageSchemaProperties page,
            String actionName) {
        assertThat(page).isNotNull();
        assertThat(page.getForm()).isNotNull();
        List<ActionProperties> actions = page.getForm().getActions();
        assertThat(actions).isNotNull();
        assertThat(actions).anySatisfy(action -> assertThat(
                action.getName() == null ? action.getId() : action.getName())
                .isEqualTo(actionName));
    }

    private static void load(
            org.springframework.context.ConfigurableApplicationContext context,
            String resourceName) {
        try {
            new YamlPropertySourceLoader().load(
                            resourceName,
                            new ClassPathResource(resourceName))
                    .forEach(source -> context.getEnvironment()
                            .getPropertySources().addLast(source));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Failed to load " + resourceName, exception);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({
            BffPagesProperties.class,
            WorkflowErrorContractProperties.class
    })
    static class TestConfig {
    }
}
