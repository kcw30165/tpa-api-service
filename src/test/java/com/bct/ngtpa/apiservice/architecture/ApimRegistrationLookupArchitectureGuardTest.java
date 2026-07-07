package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.ApimRegistrationLookupAdapter;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupCommand;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationLookupPort;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ApimRegistrationLookupArchitectureGuardTest {

    @Test
    void adapterDoesNotUseRawWebClientDirectly() {
        var classes = new ClassFileImporter().importPackages("com.bct.ngtpa.apiservice.adapter.out.apim.registration");

        ArchRuleDefinition.noClasses()
                .that().resideInAPackage("..adapter.out.apim.registration..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.web.reactive.function.client..")
                .check(classes);
        assertThat(RegistrationLookupPort.class).isAssignableFrom(ApimRegistrationLookupAdapter.class);
    }

    @Test
    void applicationRegistrationLookupStillHasNoAdapterDependency() {
        var classes = new ClassFileImporter().importClasses(
                RegistrationLookupCommand.class,
                com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupResult.class,
                com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupAccount.class,
                RegistrationLookupPort.class);

        ArchRuleDefinition.noClasses()
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.bct.ngtpa.apiservice.adapter..",
                        "org.springframework..",
                        "org.springframework.web..",
                        "org.springframework.http..",
                        "jakarta.servlet..",
                        "io.lettuce..",
                        "org.springframework.data.redis..")
                .check(classes);
    }

    @Test
    void apimRegistrationLookupAdapterDoesNotDependOnWebRedisSmsEmailAdapters() {
        var classes = new ClassFileImporter().importPackages("com.bct.ngtpa.apiservice.adapter.out.apim.registration");

        ArchRuleDefinition.noClasses()
                .that().resideInAPackage("..adapter.out.apim.registration..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..adapter.in.web..",
                        "..adapter.out.redis..",
                        "..adapter.out.sms..",
                        "..adapter.out.email..")
                .check(classes);
    }

    @Test
    void p6_5DoesNotIntroduceSessionOtpMessageCookieOrCsrfRuntime() throws IOException {
        assertThat(sourceContainsInRegistrationUsecase("AuthFlowSessionLifecycleService")).isFalse();
        assertThat(sourceContainsInRegistrationUsecase("AuthFlowSessionConcurrencyService")).isFalse();
        assertThat(sourceContainsInRegistrationUsecase("AuthFlowSessionCsrfService")).isFalse();
        assertThat(sourceContainsInRegistrationUsecase("OtpChallengePort")).isFalse();
        assertThat(sourceContainsInRegistrationUsecase("MessageDeliveryPort")).isFalse();
        assertThat(sourceContainsInRegistrationUsecase("HttpSessionCookieAdapter")).isFalse();
    }

    @Test
    void p6_5DoesNotIntroduceAuthFlowResumeRuntime() throws IOException {
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.in.web.request.AuthFlowResumeRequest");
        assertClassAbsent("com.bct.ngtpa.apiservice.application.port.in.ResumeAuthFlowUseCase");
        assertClassAbsent("com.bct.ngtpa.apiservice.application.port.out.AuthFlowResumePort");
        assertClassAbsent("com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowResumeService");
        assertThat(sourceContains("/api/v1/auth/flow/resume")).isFalse();
    }

    private static void assertClassAbsent(String className) {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(className));
    }

    private static boolean sourceContainsInRegistrationUsecase(String text) throws IOException {
        return sourceContainsUnder(Path.of("src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration"), text);
    }

    private static boolean sourceContains(String text) throws IOException {
        return sourceContainsUnder(Path.of("src/main"), text);
    }

    private static boolean sourceContainsUnder(Path root, String text) throws IOException {
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
