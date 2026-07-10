package com.bct.ngtpa.apiservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RedisCaptchaChallengeArchitectureGuardTest {

    @Test
    void redisCaptchaAdapterDoesNotReadAuthWorkflowPolicyDirectly() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bct.ngtpa.apiservice.adapter.out.redis.auth");

        noClasses()
                .that()
                .resideInAPackage("..adapter.out.redis.auth..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.bct.ngtpa.apiservice.application.port.in.AuthWorkflowPolicyPort")
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.bct.ngtpa.apiservice.application.dto.auth.RegistrationAuthWorkflowPolicy")
                .check(classes);
    }

    @Test
    void redisCaptchaAdapterDoesNotDependOnWebApimSmsEmailAdapters() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bct.ngtpa.apiservice.adapter.out.redis.auth");

        noClasses()
                .that()
                .resideInAPackage("..adapter.out.redis.auth..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "..adapter.in.web..",
                        "..adapter.out.apim..",
                        "..adapter.out.sms..",
                        "..adapter.out.email..").check(classes);
    }

    @Test
    void captchaApplicationModelStillHasNoRedisSpringOrWebDependency() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(
                        "com.bct.ngtpa.apiservice.application.dto.auth.captcha",
                        "com.bct.ngtpa.apiservice.application.port.out");

        noClasses()
                .that()
                .resideInAPackage("..application.dto.auth.captcha..")
                .or()
                .resideInAPackage("..application.port.out..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "org.springframework.http..",
                        "org.springframework.web..",
                        "jakarta.servlet..",
                        "io.lettuce..",
                        "org.springframework.data.redis..",
                        "com.bct.ngtpa.apiservice.adapter..").check(classes);
    }

    @Test
    void p8_5AllowsControllerEndpointsWithoutChangingRedisBoundary() throws Exception {
        assertClassPresent("com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthCaptchaController");
    }

    @Test
    void p8_3DoesNotIntroduceFutureRegistrationEndpoints() throws Exception {
        assertClassAbsent("com.bct.ngtpa.apiservice.application.port.in.CompleteRegistrationUseCase");
        assertClassAbsent("com.bct.ngtpa.apiservice.application.port.in.CancelRegistrationUseCase");
        assertClassAbsent("com.bct.ngtpa.apiservice.application.port.in.ResumeAuthFlowUseCase");
        assertThat(sourceContains("/api/v1/auth/registration/complete")).isFalse();
        assertThat(sourceContains("/api/v1/auth/registration/cancel")).isFalse();
        assertThat(sourceContains("/api/v1/auth/flow/resume")).isFalse();
    }

    private static void assertClassAbsent(String className) throws Exception {
        try {
            Class.forName(className);
            throw new AssertionError(className + " should not exist");
        } catch (ClassNotFoundException expected) {
            assertThat(expected).isNotNull();
        }
    }

    private static void assertClassPresent(String className) throws Exception {
        assertThat(Class.forName(className)).isNotNull();
    }

    private static boolean sourceContains(String text) throws Exception {
        Path root = Path.of("src/main");
        if (!Files.exists(root)) {
            return false;
        }
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java") || path.toString().endsWith(".yml") || path.toString().endsWith(".yaml"))
                    .anyMatch(path -> contains(path, text));
        }
    }

    private static boolean contains(Path path, String text) {
        try {
            return Files.readString(path).contains(text);
        } catch (Exception exception) {
            return false;
        }
    }
}