package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationService;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StartRegistrationUseCaseArchitectureGuardTest {
    @Test
    void startRegistrationUseCaseHasNoWebRedisApimAdapterOrCookieDependencies() {
        var classes = new ClassFileImporter().importPackages("com.bct.ngtpa.apiservice.application.usecase.auth.registration");
        ArchRuleDefinition.noClasses()
                .that().resideInAPackage("..application.usecase.auth.registration..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..adapter.in.web..", "..adapter.out.redis..", "..adapter.out.apim..",
                        "org.springframework..", "org.springframework.http..", "org.springframework.web..",
                        "jakarta.servlet..", "io.lettuce..", "org.springframework.data.redis..")
                .check(classes);
        assertThat(StartRegistrationService.class).isNotNull();
    }

    @Test
    void p6_6DoesNotIntroduceControllerOrEndpoint() throws IOException {
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthRegistrationOtpController");
        assertThat(sourceContains("/api/v1/auth/registration/start")).isFalse();
    }

    @Test
    void p6_6DoesNotIntroduceAuthFlowResumeCaptchaOrLaterRegistrationUseCases() throws IOException {
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.in.web.request.AuthFlowResumeRequest");
        assertClassAbsent("com.bct.ngtpa.apiservice.application.port.in.ResumeAuthFlowUseCase");
        assertClassAbsent("com.bct.ngtpa.apiservice.application.port.out.CaptchaChallengePort");
        assertClassAbsent("com.bct.ngtpa.apiservice.application.port.in.ResendRegistrationOtpUseCase");
        assertClassAbsent("com.bct.ngtpa.apiservice.application.port.in.VerifyRegistrationOtpUseCase");
        assertClassAbsent("com.bct.ngtpa.apiservice.application.port.in.CheckRegistrationUsernameUseCase");
        assertClassAbsent("com.bct.ngtpa.apiservice.application.port.in.CompleteRegistrationUseCase");
        assertClassAbsent("com.bct.ngtpa.apiservice.application.port.in.CancelRegistrationUseCase");
        assertThat(sourceContains("/api/v1/auth/flow/resume")).isFalse();
    }

    @Test
    void useCaseDoesNotHardcodeUserFacingMessages() throws IOException {
        Path source = Path.of("src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/StartRegistrationUseCase.java");
        if (Files.exists(source)) {
            String text = Files.readString(source);
            assertThat(text).doesNotContain("We are unable to verify your details", "A new verification code has been sent", "我們未能核實您的資料");
        }
    }

    private static void assertClassAbsent(String className) { assertThrows(ClassNotFoundException.class, () -> Class.forName(className)); }
    private static boolean sourceContains(String text) throws IOException {
        Path root = Path.of("src/main");
        if (!Files.exists(root)) return false;
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java") || path.toString().endsWith(".yml") || path.toString().endsWith(".yaml"))
                    .anyMatch(path -> contains(path, text));
        }
    }
    private static boolean contains(Path path, String text) { try { return Files.readString(path).contains(text); } catch (IOException exception) { return false; } }
}
