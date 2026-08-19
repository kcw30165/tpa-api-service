package com.bct.ngtpa.apiservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "com.bct.ngtpa.apiservice",
    importOptions = ImportOption.DoNotIncludeTests.class)
class AuthWorkflowRateLimitPolicyArchitectureTest {

  @ArchTest
  static final ArchRule registrationRateLimitPolicyRecordsAreFrameworkFree =
      noClasses()
          .that()
          .resideInAPackage("..application.dto.auth..")
          .and()
          .haveNameMatching(".*RateLimit.*Policy")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "com.bct.ngtpa.apiservice.adapter..",
              "io.lettuce..",
              "org.springframework.data.redis..")
          .as(
              "P3.1 application rate-limit policy records must remain framework-free and adapter-free");
}
