package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bct.ngtpa.apiservice.adapter.out.redis.auth.RedisRegistrationIdempotencyAdapter;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyRecord;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationIdempotencyPort;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RedisRegistrationIdempotencyArchitectureGuardTest {

    @Test
    void redisRegistrationIdempotencyAdapterDoesNotDependOnWebApimSmsEmailAdapters() {
        var classes = new ClassFileImporter().importPackages("com.bct.ngtpa.apiservice.adapter.out.redis.auth");

        ArchRuleDefinition.noClasses()
                .that().resideInAPackage("..adapter.out.redis.auth..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..adapter.in.web..",
                        "..adapter.out.apim..",
                        "..adapter.out.sms..",
                        "..adapter.out.email..")
                .check(classes);
        assertThat(RegistrationIdempotencyPort.class).isAssignableFrom(RedisRegistrationIdempotencyAdapter.class);
    }

    @Test
    void applicationRegistrationIdempotencyModelStillHasNoRedisSpringOrWebDependency() {
        var classes = new ClassFileImporter().importClasses(
                RegistrationIdempotencyRecord.class,
                RegistrationIdempotencyPort.class);

        ArchRuleDefinition.noClasses()
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "org.springframework.http..",
                        "org.springframework.web..",
                        "jakarta.servlet..",
                        "io.lettuce..",
                        "org.springframework.data.redis..",
                        "com.bct.ngtpa.apiservice.adapter..")
                .check(classes);
    }

    @Test
    void p6_2DoesNotIntroduceRegistrationControllerOrApimAdapter() {
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthRegistrationController");
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthRegistrationOtpController");
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.out.apim.ApimRegistrationStartAdapter");
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.out.apim.ApimRegistrationVerificationAdapter");
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.out.apim.TRPSearchUnregisteredMemberAdapter");
    }

    @Test
    void p6_2DoesNotIntroduceAuthFlowResumeOrCaptcha() throws IOException {
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
