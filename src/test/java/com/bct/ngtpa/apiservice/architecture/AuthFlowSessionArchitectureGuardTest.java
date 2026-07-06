package com.bct.ngtpa.apiservice.architecture;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

class AuthFlowSessionArchitectureGuardTest {
    @Test
    void authFlowSessionApplicationModelHasNoSpringAdapterRedisOrWebDependencies() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.bct.ngtpa.apiservice.application.dto.session.authflow", "com.bct.ngtpa.apiservice.application.port.out");
        ArchRuleDefinition.noClasses()
                .that().resideInAnyPackage("..application.dto.session.authflow..", "..application.port.out..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "org.springframework.http..", "org.springframework.web..", "jakarta.servlet..", "com.bct.ngtpa.apiservice.adapter..", "io.lettuce..", "org.springframework.data.redis..")
                .check(classes);
    }

    @Test
    void p4b1DoesNotIntroduceRedisOrCookieRuntime() {
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthRegistrationOtpController");
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthCaptchaController");
    }

    @Test
    void authFlowSessionIsNotAuthenticatedSession() {
        assertClassAbsent("com.bct.ngtpa.apiservice.application.dto.session.AuthenticatedSession");
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.out.redis.auth.RedisAuthenticatedSessionAdapter");
    }

    private static void assertClassAbsent(String className) {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(className));
    }
}
