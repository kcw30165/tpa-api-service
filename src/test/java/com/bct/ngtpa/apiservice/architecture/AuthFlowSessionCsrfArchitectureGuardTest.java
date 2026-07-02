package com.bct.ngtpa.apiservice.architecture;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

class AuthFlowSessionCsrfArchitectureGuardTest {

    @Test
    void authFlowSessionCsrfServiceHasNoSpringRedisWebOrAdapterDependencies() {
        JavaClasses classes = new ClassFileImporter().importPackages(
                "com.bct.ngtpa.apiservice.application.usecase.session.authflow");

        ArchRuleDefinition.noClasses()
                .that().resideInAPackage("..application.usecase.session.authflow..")
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
    void p4b3DoesNotIntroduceCookieOrEndpointRuntime() {
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.in.web.filter.RegistrationCsrfWebFilter");
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthRegistrationController");
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthRegistrationOtpController");
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.in.web.session.cookie.AuthFlowCookieReader");
    }

    private static void assertClassAbsent(String className) {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(className));
    }
}
