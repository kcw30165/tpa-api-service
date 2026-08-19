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
class MessageDeliveryApplicationBoundaryArchitectureTest {

  @ArchTest
  static final ArchRule messageDeliveryModelHasNoSpringOrAdapterDependencies =
      noClasses()
          .that()
          .resideInAnyPackage("..application.dto.message..", "..application.port.out")
          .and()
          .haveNameMatching(".*MessageDelivery.*")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "com.bct.ngtpa.apiservice.adapter..",
              "io.lettuce..",
              "org.springframework.data.redis..",
              "jakarta.mail..")
          .as(
              "Message delivery application DTOs and ports must remain framework-free and adapter-free.");

  @Test
  void messageDeliveryModelHasNoSpringOrAdapterDependencies() {
    messageDeliveryModelHasNoSpringOrAdapterDependencies.check(
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.bct.ngtpa.apiservice"));
  }
}
