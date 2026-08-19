package com.bct.ngtpa.apiservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "com.bct.ngtpa.apiservice",
    importOptions = ImportOption.DoNotIncludeTests.class)
class OtpGenerationHashingArchitectureGuardTest {

  @ArchTest
  static final ArchRule otpGenerationAndHashingHaveNoSpringOrAdapterDependencies =
      noClasses()
          .that()
          .resideInAnyPackage("..application.service.auth.otp..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "..adapter..",
              "io.lettuce..",
              "org.springframework.data.redis..")
          .as("OTP generation and hashing helpers must remain framework-free and adapter-free.");
}
