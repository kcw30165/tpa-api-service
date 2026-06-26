package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.registration;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.ConfigBackedAuthWorkflowPolicyAdapter;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ActionProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.FieldProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PageSchemaLinter;
import com.bct.ngtpa.apiservice.adapter.in.web.response.RegistrationSuccessPageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistrationFoundationIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withInitializer(context -> loadYaml(context, "application.yml"))
            .withInitializer(context -> loadYaml(context, "application-page-registration.yml"));

    @Test
    void foundationBindsAuthWorkflowAndRegistrationPages() {
        contextRunner.run(context -> {
            AuthWorkflowProperties authWorkflowProperties = context.getBean(AuthWorkflowProperties.class);
            ConfigBackedAuthWorkflowPolicyAdapter policyAdapter = new ConfigBackedAuthWorkflowPolicyAdapter(authWorkflowProperties);
            BffPagesProperties pageProperties = context.getBean(BffPagesProperties.class);

            assertFalse(policyAdapter.registrationPolicy().preStartChallenge().enabled());
            assertEquals(com.bct.ngtpa.apiservice.application.dto.auth.PreStartChallengeType.CAPTCHA,
                    policyAdapter.registrationPolicy().preStartChallenge().type());
            assertEquals("UB", policyAdapter.resolveIdentityTypeSearchScope("GPN").orElseThrow());
            assertEquals("TL", policyAdapter.resolveIdentityTypeSearchScope("GPFS").orElseThrow());
            assertEquals("MG_MC_DB", policyAdapter.resolveIdentityTypeSearchScope("STAFF_NO").orElseThrow());
            assertEquals("OI_OS_TB_OU_JP_HS_JM", policyAdapter.resolveIdentityTypeSearchScope("HKID").orElseThrow());
            assertEquals("OI_OS_TB_OU_JP_HS_JM", policyAdapter.resolveIdentityTypeSearchScope("PASSPORT").orElseThrow());
            assertEquals("OI_OS_TB_OU_JP_HS_JM", policyAdapter.resolveIdentityTypeSearchScope("OTHER_NATIONAL_ID").orElseThrow());

            assertTrue(pageProperties.getPages().containsKey("registration-start"));
            assertTrue(pageProperties.getPages().containsKey("registration-otp-verification"));
            assertTrue(pageProperties.getPages().containsKey("registration-trust-selection"));
            assertTrue(pageProperties.getPages().containsKey("registration-username-check"));
            assertTrue(pageProperties.getPages().containsKey("registration-password-setup"));
            assertTrue(pageProperties.getPages().containsKey("registration-success"));
        });
    }

    @Test
    void registrationPageConfigLintsWithSuccessVariants() {
        contextRunner.run(context -> {
            BffPagesProperties pageProperties = context.getBean(BffPagesProperties.class);

            assertTrue(PageSchemaLinter.lint(pageProperties).getErrors().isEmpty());
            assertTrue(pageProperties.getPages().get("registration-success").getVariants().containsKey("noRemainingTrusts"));
            assertTrue(pageProperties.getPages().get("registration-success").getVariants().containsKey("hasRemainingTrusts"));
        });
    }

    @Test
    void registrationSuccessMapperMapsBothVariantsFromBffPages() {
        contextRunner.run(context -> {
            RegistrationSuccessPageMapper mapper = new RegistrationSuccessPageMapper(context.getBean(BffPagesProperties.class));

            RegistrationSuccessPageResponse noRemainingTrusts = mapper.map("registration-success", "noRemainingTrusts", "en");
            assertEquals("registration-success", noRemainingTrusts.pageId());
            assertEquals("noRemainingTrusts", noRemainingTrusts.variant());
            assertEquals(1, noRemainingTrusts.actions().size());
            assertEquals("returnToLogin", noRemainingTrusts.actions().get(0).id());
            assertEquals("PRIMARY", noRemainingTrusts.actions().get(0).type());
            assertEquals("RETURN_TO_LOGIN", noRemainingTrusts.actions().get(0).nextStep());

            RegistrationSuccessPageResponse hasRemainingTrusts = mapper.map("registration-success", "hasRemainingTrusts", "en");
            assertEquals("registration-success", hasRemainingTrusts.pageId());
            assertEquals("hasRemainingTrusts", hasRemainingTrusts.variant());
            assertEquals(2, hasRemainingTrusts.actions().size());
            assertEquals("continueRegistration", hasRemainingTrusts.actions().get(0).id());
            assertEquals("PRIMARY", hasRemainingTrusts.actions().get(0).type());
            assertEquals("SELECT_TRUST", hasRemainingTrusts.actions().get(0).nextStep());
            assertEquals("returnToLogin", hasRemainingTrusts.actions().get(1).id());
            assertEquals("SECONDARY", hasRemainingTrusts.actions().get(1).type());
            assertEquals("RETURN_TO_LOGIN", hasRemainingTrusts.actions().get(1).nextStep());
        });
    }

    @Test
    void registrationTrustSelectionPageHasNoSubmitActions() {
        contextRunner.run(context -> {
            var trustSelectionPage = context.getBean(BffPagesProperties.class).getPages().get("registration-trust-selection");

            assertTrue(trustSelectionPage != null);
            List<ActionProperties> actions = trustSelectionPage.getForm() == null
                    ? List.of()
                    : trustSelectionPage.getForm().getActions() == null ? List.of() : trustSelectionPage.getForm().getActions();
            List<String> actionIds = actions.stream()
                    .map(action -> action.getId() != null ? action.getId() : action.getName())
                    .toList();

            assertFalse(actionIds.contains("continue"));
            assertFalse(actionIds.contains("cancelRegistration"));
        });
    }

    @Test
    void registrationStartPageUsesSeparateMobileAndEmailFields() {
        contextRunner.run(context -> {
            var startPage = context.getBean(BffPagesProperties.class).getPages().get("registration-start");
            List<FieldProperties> fields = startPage.getForm().getSections().stream()
                    .flatMap(section -> section.getFields().stream())
                    .toList();
            List<String> fieldIds = fields.stream().map(FieldProperties::getId).toList();

            assertTrue(fieldIds.contains("mobileNumber"));
            assertTrue(fieldIds.contains("emailAddress"));
            assertFalse(fieldIds.contains("contactType"));
            assertFalse(fieldIds.contains("contactValue"));
        });
    }

    private void loadYaml(org.springframework.context.ConfigurableApplicationContext context, String resourceName) {
        var resource = new ClassPathResource(resourceName);
        try {
            var propertySources = new YamlPropertySourceLoader().load(resourceName, resource);
            propertySources.forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Failed to load YAML " + resourceName, ex);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({AuthWorkflowProperties.class, BffPagesProperties.class})
    static class TestConfig {
    }
}