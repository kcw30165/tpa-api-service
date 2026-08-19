package com.bct.ngtpa.apiservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "com.bct.ngtpa.apiservice",
    importOptions = ImportOption.DoNotIncludeTests.class)
class RedisOtpChallengeArchitectureGuardTest {

  @ArchTest
  static final ArchRule redisOtpAdapterDoesNotDependOnWebOrApim =
      noClasses()
          .that()
          .resideInAnyPackage("..adapter.out.redis.auth..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..adapter.in.web..", "..adapter.out.apim..")
          .as("Redis OTP adapter must not depend on web or APIM adapters.");
}
