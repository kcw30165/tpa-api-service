package com.bct.ngtpa.apiservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

@AnalyzeClasses(
    packages = "com.bct.ngtpa.apiservice",
    importOptions = ImportOption.DoNotIncludeTests.class)
class MessageDeliveryArchitectureGuardTest {

  @ArchTest
  static final ArchRule smsProviderDtosDoNotLeakOutsideSmsAdapter =
      noClasses()
          .that()
          .resideInAnyPackage(
              "..application..",
              "..domain..",
              "..adapter.in.web..",
              "..adapter.out.email..",
              "..adapter.out.apim..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..adapter.out.sms.dto..");

  @ArchTest
  static final ArchRule emailProviderTypesDoNotLeakOutsideEmailAdapter =
      noClasses()
          .that()
          .resideInAnyPackage(
              "..application..",
              "..domain..",
              "..adapter.in.web..",
              "..adapter.out.sms..",
              "..adapter.out.apim..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..adapter.out.email..");

  @ArchTest
  static final ArchRule messageDeliveryApplicationLayerHasNoFrameworkOrAdapterDependencies =
      noClasses()
          .that()
          .resideInAnyPackage(
              "..application.dto.message..",
              "..application.service.message..",
              "..application.usecase.message..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "com.bct.ngtpa.apiservice.adapter..",
              "io.lettuce..",
              "org.springframework.data.redis..",
              "jakarta.mail..",
              "javax.mail..",
              "org.springframework.mail..",
              "org.springframework.web.reactive.function.client..");

  @Test
  void smsProviderDtosDoNotLeakOutsideSmsAdapter() {
    smsProviderDtosDoNotLeakOutsideSmsAdapter.check(importedClasses());
  }

  @Test
  void emailProviderTypesDoNotLeakOutsideEmailAdapter() {
    emailProviderTypesDoNotLeakOutsideEmailAdapter.check(importedClasses());
  }

  @Test
  void messageDeliveryApplicationLayerHasNoFrameworkOrAdapterDependencies() {
    messageDeliveryApplicationLayerHasNoFrameworkOrAdapterDependencies.check(importedClasses());
  }

  private com.tngtech.archunit.core.domain.JavaClasses importedClasses() {
    return new ClassFileImporter()
        .withImportOption(new ImportOption.DoNotIncludeTests())
        .importPackages("com.bct.ngtpa.apiservice");
  }
}
