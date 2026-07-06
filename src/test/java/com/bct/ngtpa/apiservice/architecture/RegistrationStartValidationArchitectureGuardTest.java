package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationStartCommand;
import com.bct.ngtpa.apiservice.application.service.auth.registration.RegistrationStartValidator;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationStartValidationArchitectureGuardTest {

    @Test
    void registrationStartValidationHasNoSpringRedisApimOrWebDependencies() {
        var classes = new ClassFileImporter().importPackages(
                "com.bct.ngtpa.apiservice.application.dto.auth.registration",
                "com.bct.ngtpa.apiservice.application.service.auth.registration");

        ArchRuleDefinition.noClasses()
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "org.springframework.http..",
                        "org.springframework.web..",
                        "jakarta.servlet..",
                        "com.bct.ngtpa.apiservice.adapter..",
                        "com.bct.ngtpa.apiservice.adapter.out.apim..",
                        "io.lettuce..",
                        "org.springframework.data.redis..",
                        "org.slf4j..")
                .check(classes);
        assertThat(RegistrationStartCommand.class).isNotNull();
        assertThat(RegistrationStartValidator.class).isNotNull();
    }

    @Test
    void p6_3DoesNotIntroduceUseCaseControllerApimRedisAdapterOrEndpoint() throws IOException {
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthRegistrationOtpController");
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.out.apim.ApimRegistrationStartAdapter");
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.out.apim.TRPSearchUnregisteredMemberAdapter");
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.out.redis.auth.RedisRegistrationStartAdapter");
        assertThat(sourceContains("/api/v1/auth/registration/start")).isFalse();
    }

    @Test
    void p6_3DoesNotIntroduceAuthFlowResumeOrCaptchaRuntime() throws IOException {
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.in.web.request.AuthFlowResumeRequest");
        assertClassAbsent("com.bct.ngtpa.apiservice.application.port.in.ResumeAuthFlowUseCase");
        assertClassAbsent("com.bct.ngtpa.apiservice.application.port.out.AuthFlowResumePort");
        assertClassAbsent("com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowResumeService");
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthCaptchaController");
        assertClassAbsent("com.bct.ngtpa.apiservice.application.port.out.CaptchaChallengePort");
        assertThat(sourceContains("/api/v1/auth/flow/resume")).isFalse();
    }

    private static void assertClassAbsent(String className) {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(className));
    }

    private static boolean sourceContains(String text) throws IOException {
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
        } catch (IOException exception) {
            return false;
        }
    }
}
