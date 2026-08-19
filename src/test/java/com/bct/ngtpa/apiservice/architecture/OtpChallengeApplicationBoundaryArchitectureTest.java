package com.bct.ngtpa.apiservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "com.bct.ngtpa.apiservice",
    importOptions = ImportOption.DoNotIncludeTests.class)
class OtpChallengeApplicationBoundaryArchitectureTest {

  @ArchTest
  static final ArchRule otpApplicationModelHasNoSpringOrAdapterDependencies =
      noClasses()
          .that()
          .resideInAnyPackage("..application.dto.auth.otp..", "..application.port.out")
          .and()
          .haveNameMatching("Otp.*|.*OtpChallenge.*")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "..adapter..",
              "io.lettuce..",
              "org.springframework.data.redis..")
          .as("OTP application DTOs and ports must remain framework-free and adapter-free.");
}
