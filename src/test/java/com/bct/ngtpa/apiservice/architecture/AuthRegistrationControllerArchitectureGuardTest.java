package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthRegistrationControllerArchitectureGuardTest {
    @Test
    void authRegistrationControllerDependsOnlyOnUseCaseAndWebMappers() {
        var classes = new ClassFileImporter().importPackages("com.bct.ngtpa.apiservice.adapter.in.web.auth.registration");
        ArchRuleDefinition.noClasses()
                .that().resideInAPackage("..adapter.in.web.auth.registration..")
                .should().dependOnClassesThat().resideInAnyPackage("..adapter.out.apim..", "..adapter.out.redis..", "..adapter.out.sms..", "..adapter.out.email..", "org.springframework.data.redis..", "org.springframework.web.reactive.function.client..")
                .check(classes);
    }

    @Test
    void p6_7DoesNotIntroduceAuthFlowResumeEndpoints() throws IOException {
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.in.web.request.AuthFlowResumeRequest");
        assertClassAbsent("com.bct.ngtpa.apiservice.application.port.in.ResumeAuthFlowUseCase");
        assertThat(sourceContains("/api/v1/auth/flow/resume")).isFalse();
    }

    @Test
    void controllerDoesNotHardcodeUserFacingMessages() throws IOException {
        Path root = Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/registration");
        if (Files.exists(root)) {
            try (var paths = Files.walk(root)) {
                var text = paths.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".java")).map(AuthRegistrationControllerArchitectureGuardTest::read).reduce("", String::concat);
                assertThat(text).doesNotContain("We are unable to verify your details", "A new verification code has been sent", "我們未能核實您的資料");
            }
        }
    }

    private static void assertClassAbsent(String className) { assertThrows(ClassNotFoundException.class, () -> Class.forName(className)); }
    private static boolean sourceContains(String text) throws IOException {
        Path root = Path.of("src/main");
        if (!Files.exists(root)) return false;
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".java") || path.toString().endsWith(".yml") || path.toString().endsWith(".yaml")).anyMatch(path -> read(path).contains(text));
        }
    }
    private static String read(Path path) { try { return Files.readString(path); } catch (IOException exception) { return ""; } }
}
