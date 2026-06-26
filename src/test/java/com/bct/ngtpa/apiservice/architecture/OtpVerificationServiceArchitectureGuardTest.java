package com.bct.ngtpa.apiservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.bct.ngtpa.apiservice", importOptions = ImportOption.DoNotIncludeTests.class)
class OtpVerificationServiceArchitectureGuardTest {

    @ArchTest
    static final ArchRule otpVerificationServiceHasNoSpringOrAdapterDependencies = noClasses()
            .that().resideInAnyPackage("..application.usecase.auth.otp..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "..adapter..",
                    "io.lettuce..",
                    "org.springframework.data.redis..")
            .as("OTP verification service must remain framework-free and adapter-free.");
}