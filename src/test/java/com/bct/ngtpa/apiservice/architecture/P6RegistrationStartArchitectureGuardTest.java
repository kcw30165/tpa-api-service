package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class P6RegistrationStartArchitectureGuardTest {
    @Test
    void p6ApplicationUseCaseRemainsFrameworkFree() {
        var classes = new ClassFileImporter().importPackages("com.bct.ngtpa.apiservice.application.usecase.auth.registration");
        ArchRuleDefinition.noClasses().that().resideInAPackage("..application.usecase.auth.registration..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "org.springframework.http..", "org.springframework.web..", "jakarta.servlet..", "..adapter..", "io.lettuce..", "org.springframework.data.redis..")
                .check(classes);
    }

    @Test
    void p6ControllerDoesNotDependOnOutboundAdapters() {
        var classes = new ClassFileImporter().importPackages("com.bct.ngtpa.apiservice.adapter.in.web.auth.registration");
        ArchRuleDefinition.noClasses().that().resideInAPackage("..adapter.in.web.auth.registration..")
                .should().dependOnClassesThat().resideInAnyPackage("..adapter.out.apim..", "..adapter.out.redis..", "..adapter.out.sms..", "..adapter.out.email..", "org.springframework.data.redis..", "org.springframework.web.reactive.function.client..")
                .check(classes);
    }

    @Test
    void p6DoesNotIntroduceOtherRegistrationAuthFlowResumeOrCaptchaEndpoints() throws IOException {
        for (String path : java.util.List.of(
                "/api/v1/auth/registration/otp/resend",
                "/api/v1/auth/registration/otp/verify",
                "/api/v1/auth/registration/username/check",
                "/api/v1/auth/registration/complete",
                "/api/v1/auth/registration/cancel",
                "/api/v1/auth/flow/resume",
                "/api/v1/auth/captcha/challenge",
                "/api/v1/auth/captcha/verify",
                "/api/v1/auth/captcha/refresh")) {
            assertThat(sourceContains(path)).as(path).isFalse();
        }
        assertClassAbsent("com.bct.ngtpa.apiservice.application.port.in.ResumeAuthFlowUseCase");
        assertClassAbsent("com.bct.ngtpa.apiservice.application.port.out.CaptchaChallengePort");
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthCaptchaController");
    }

    private static void assertClassAbsent(String className) { assertThrows(ClassNotFoundException.class, () -> Class.forName(className)); }
    private static boolean sourceContains(String text) throws IOException {
        Path root = Path.of("src/main");
        if (!Files.exists(root)) return false;
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java") || path.toString().endsWith(".yml") || path.toString().endsWith(".yaml"))
                    .anyMatch(path -> read(path).contains(text));
        }
    }
    private static String read(Path path) { try { return Files.readString(path); } catch (IOException exception) { return ""; } }
}
