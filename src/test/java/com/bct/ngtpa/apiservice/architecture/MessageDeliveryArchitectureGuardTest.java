package com.bct.ngtpa.apiservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

@AnalyzeClasses(packages = "com.bct.ngtpa.apiservice", importOptions = ImportOption.DoNotIncludeTests.class)
class MessageDeliveryArchitectureGuardTest {

    @ArchTest
    static final ArchRule smsProviderDtosDoNotLeakOutsideSmsAdapter = noClasses()
            .that().resideInAnyPackage(
                    "..application..",
                    "..domain..",
                    "..adapter.in.web..",
                    "..adapter.out.email..",
                    "..adapter.out.apim..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.out.sms.dto..");

    @ArchTest
    static final ArchRule emailProviderTypesDoNotLeakOutsideEmailAdapter = noClasses()
            .that().resideInAnyPackage(
                    "..application..",
                    "..domain..",
                    "..adapter.in.web..",
                    "..adapter.out.sms..",
                    "..adapter.out.apim..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.out.email..");

    @ArchTest
    static final ArchRule messageDeliveryApplicationLayerHasNoFrameworkOrAdapterDependencies = noClasses()
            .that().resideInAnyPackage(
                    "..application.dto.message..",
                    "..application.service.message..",
                    "..application.usecase.message..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "com.bct.ngtpa.apiservice.adapter..",
                    "io.lettuce..",
                    "org.springframework.data.redis..",
                    "jakarta.mail..",
                    "javax.mail..",
                    "org.springframework.mail..",
                    "org.springframework.web.reactive.function.client..");

    @Test
    void smsProviderDtosDoNotLeakOutsideSmsAdapter() {
        smsProviderDtosDoNotLeakOutsideSmsAdapter.check(importedClasses());
    }

    @Test
    void emailProviderTypesDoNotLeakOutsideEmailAdapter() {
        emailProviderTypesDoNotLeakOutsideEmailAdapter.check(importedClasses());
    }

    @Test
    void messageDeliveryApplicationLayerHasNoFrameworkOrAdapterDependencies() {
        messageDeliveryApplicationLayerHasNoFrameworkOrAdapterDependencies.check(importedClasses());
    }

    @Test
    void p2DoesNotIntroduceRegistrationEndpoints() {
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.in.web.auth.AuthRegistrationController");
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.in.web.auth.AuthRegistrationOtpController");
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.in.web.auth.AuthCaptchaController");
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthRegistrationController");
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthRegistrationOtpController");
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthCaptchaController");
    }

    @Test
    void p2DoesNotIntroduceApimRegistrationAdapters() {
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.out.apim.ApimRegistrationVerificationAdapter");
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.out.apim.ApimRegistrationCredentialAdapter");
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.out.apim.registration.ApimRegistrationVerificationAdapter");
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.out.apim.registration.ApimRegistrationCredentialAdapter");
    }

    @Test
    void p2DoesNotIntroduceCaptchaImplementation() {
        assertClassAbsent("com.bct.ngtpa.apiservice.application.port.out.CaptchaChallengePort");
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.out.redis.CaptchaChallengeRedisAdapter");
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.in.web.auth.AuthCaptchaController");
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthCaptchaController");
    }

    private com.tngtech.archunit.core.domain.JavaClasses importedClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bct.ngtpa.apiservice");
    }

    private void assertClassAbsent(String className) {
        try {
            Class.forName(className);
            assertThat(true).as(className + " should not exist in P2 message delivery foundation").isFalse();
        } catch (ClassNotFoundException expected) {
            assertThat(expected).isNotNull();
        }
    }
}
