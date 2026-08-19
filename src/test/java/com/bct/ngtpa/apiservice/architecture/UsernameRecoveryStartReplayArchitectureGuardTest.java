package com.bct.ngtpa.apiservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Architecture guard for the Repair 019B secure START replay contract (Candidate A).
 *
 * <p>Locks the committed replay boundary that {@code UsernameRecoveryStartReplayCodec} owns: the
 * codec must stay framework-free, adapter-free, and infrastructure-free; it must depend only on
 * Java, the idempotency record, and the session-id domain model; and it must never depend on the
 * shared CSRF generation/hashing/validation abstractions, the CSRF hash domain value, or Base64.
 * That guarantees the completed replay snapshot cannot silently start carrying raw CSRF material
 * while the codec remains the only writer of the START {@code ReplaySnapshot}.
 */
class UsernameRecoveryStartReplayArchitectureGuardTest {

  private static final String CODEC = "UsernameRecoveryStartReplayCodec";
  private static final String CODEC_PACKAGE = "..application.usecase.auth.usernamerecovery..";

  @Test
  void startReplayCodecStaysFrameworkAdapterAndInfrastructureFree() {
    var classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.bct.ngtpa.apiservice");

    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage(CODEC_PACKAGE)
            .and()
            .haveSimpleName(CODEC)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "jakarta.servlet..",
                "com.bct.ngtpa.apiservice.adapter..",
                "com.bct.ngtpa.apiservice.config..",
                "com.bct.ngtpa.apiservice.infrastructure..",
                "io.lettuce..",
                "org.springframework.data.redis..",
                "org.springframework.web..",
                "org.springframework.http..")
            .as("Username Recovery START replay codec must stay framework-free and adapter-free");
    rule.check(classes);
  }

  @Test
  void startReplayCodecNeverDependsOnAuthFlowCsrfPackage() {
    var classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.bct.ngtpa.apiservice");

    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage(CODEC_PACKAGE)
            .and()
            .haveSimpleName(CODEC)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..usecase.session.authflow..")
            .as(
                "Username Recovery START replay codec must never depend on the auth-flow CSRF package");
    rule.check(classes);
  }

  @Test
  void startReplayCodecNeverDependsOnUsernameRecoveryCsrfHash() {
    var classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.bct.ngtpa.apiservice");

    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage(CODEC_PACKAGE)
            .and()
            .haveSimpleName(CODEC)
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName(
                "com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryCsrfHash")
            .as(
                "Username Recovery START replay codec must never depend on UsernameRecoveryCsrfHash");
    rule.check(classes);
  }

  @Test
  void startReplayCodecNeverDependsOnJavaUtilBase64() {
    var classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.bct.ngtpa.apiservice");

    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage(CODEC_PACKAGE)
            .and()
            .haveSimpleName(CODEC)
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("java.util.Base64")
            .as("Username Recovery START replay codec must never depend on java.util.Base64");
    rule.check(classes);
  }

  @Test
  void startReplayCodecDependencySurfaceIsJavaIdempotencyRecordAndDomainOnly() {
    var classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.bct.ngtpa.apiservice");

    ArchRule rule =
        classes()
            .that()
            .resideInAPackage(CODEC_PACKAGE)
            .and()
            .haveSimpleName(CODEC)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "java..",
                CODEC_PACKAGE,
                "com.bct.ngtpa.apiservice.application.port.out..",
                "com.bct.ngtpa.apiservice.domain.model..")
            .as(
                "Username Recovery START replay codec may depend only on Java, the idempotency record, domain models, and its own nested result types");
    rule.check(classes);
  }
}
