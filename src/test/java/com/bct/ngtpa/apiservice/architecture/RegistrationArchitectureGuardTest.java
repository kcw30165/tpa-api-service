package com.bct.ngtpa.apiservice.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertThrows;

@AnalyzeClasses(packages = "com.bct.ngtpa.apiservice", importOptions = ImportOption.DoNotIncludeTests.class)
class RegistrationArchitectureGuardTest {

    @ArchTest
    static final ArchRule applicationLayerDoesNotDependOnPageconfigOrWebResponses = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..adapter.in.web.pageconfig..",
                    "..adapter.in.web.response..")
            .as("Application layer must not depend on pageconfig or web response DTOs for registration foundation.");

    @Test
    void foundationDoesNotCreateRegistrationEndpoints() {
        assertClassMissing("com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthRegistrationOtpController");
        assertClassMissing("com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthCaptchaController");
    }

    @Test
    void foundationDoesNotCreateApplicationSuccessPageResolverPort() {
        assertClassMissing("com.bct.ngtpa.apiservice.application.port.out.RegistrationSuccessPageResolverPort");
        assertClassMissing("com.bct.ngtpa.apiservice.application.dto.RegistrationSuccessPageResponse");
    }

    @Test
    void foundationDoesNotIntroduceRuntimeAdaptersForLaterPhases() {
        assertClassMissing("com.bct.ngtpa.apiservice.adapter.out.redis.OtpChallengeRedisAdapter");
        assertClassMissing("com.bct.ngtpa.apiservice.adapter.out.redis.RegistrationSessionRedisAdapter");
        assertClassMissing("com.bct.ngtpa.apiservice.adapter.out.redis.RegistrationIdempotencyRedisAdapter");
        assertClassMissing("com.bct.ngtpa.apiservice.adapter.out.apim.ApimRegistrationVerificationAdapter");
        assertClassMissing("com.bct.ngtpa.apiservice.adapter.out.apim.ApimRegistrationCredentialAdapter");
        assertClassMissing("com.bct.ngtpa.apiservice.application.service.MessageDeliveryService");
        assertClassMissing("com.bct.ngtpa.apiservice.adapter.out.message.SmsSenderAdapter");
        assertClassMissing("com.bct.ngtpa.apiservice.adapter.out.message.EmailSenderAdapter");
        assertClassMissing("com.bct.ngtpa.apiservice.adapter.out.redis.CaptchaChallengeRedisAdapter");
    }

    private void assertClassMissing(String className) {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(className));
    }
}