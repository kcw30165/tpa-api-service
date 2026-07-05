package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpSearchUnregisteredMemberApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpSearchUnregisteredMemberApimMapper;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupCommand;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationLookupPort;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationLookupBoundaryArchitectureGuardTest {

    @Test
    void applicationRegistrationLookupHasNoSpringAdapterApimRedisOrWebDependencies() {
        var classes = new ClassFileImporter().importClasses(
                RegistrationLookupCommand.class,
                com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupResult.class,
                com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupAccount.class,
                RegistrationLookupPort.class);

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
    }

    @Test
    void apimRegistrationDtoBoundaryDoesNotUseWebRedisSmsEmailAdapters() {
        var classes = new ClassFileImporter().importPackages(
                "com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto",
                "com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper");

        ArchRuleDefinition.noClasses()
                .that().resideInAnyPackage("..adapter.out.apim.registration.dto..", "..adapter.out.apim.registration.mapper..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..adapter.in.web..",
                        "..adapter.out.redis..",
                        "..adapter.out.sms..",
                        "..adapter.out.email..")
                .check(classes);
        assertThat(TrpSearchUnregisteredMemberApimRequest.class).isNotNull();
        assertThat(TrpSearchUnregisteredMemberApimMapper.class).isNotNull();
    }

    @Test
    void p6_4DoesNotIntroduceSessionOtpMessageCookieOrCsrfRuntime() throws IOException {
        assertThat(sourceContainsInRegistrationUsecase("AuthFlowSessionLifecycleService")).isFalse();
        assertThat(sourceContainsInRegistrationUsecase("OtpChallengePort")).isFalse();
        assertThat(sourceContainsInRegistrationUsecase("MessageDeliveryPort")).isFalse();
        assertThat(sourceContainsInRegistrationUsecase("HttpSessionCookieAdapter")).isFalse();
        assertThat(sourceContainsInRegistrationUsecase("AuthFlowSessionCsrfService")).isFalse();
    }

    @Test
    void p6_4DoesNotIntroduceAuthFlowResumeOrCaptchaRuntime() throws IOException {
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

    private static boolean sourceContainsInApimRegistration(String text) throws IOException {
        return sourceContainsUnder(Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/registration"), text);
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
