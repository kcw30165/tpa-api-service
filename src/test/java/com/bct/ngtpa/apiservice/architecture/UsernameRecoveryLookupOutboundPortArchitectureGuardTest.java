package com.bct.ngtpa.apiservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryLookupPort;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Architecture guard for the Task 018 Username Recovery lookup outbound port.
 *
 * <p>Mirrors the Task 017 inbound-port guard. Verifies that the single committed lookup port stays
 * framework-free and adapter-free, depends only on Java, Reactor, and application DTOs, is
 * implemented only by the APIM adapter, and that the {@code TRPSearchMember} APIM DTOs never leave
 * {@code adapter/out/apim}. Also verifies that the lookup application boundary never references
 * {@code ApiStatus} (so {@code DOWNSTREAM_REJECTED} can never become public through this port) and
 * that the lookup DTOs remain framework-free records.
 */
class UsernameRecoveryLookupOutboundPortArchitectureGuardTest {

  @Test
  void lookupPortStaysFrameworkAdapterAndInfrastructureFree() {
    var classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.bct.ngtpa.apiservice.application.port.out");

    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..application.port.out..")
            .and()
            .haveSimpleName("UsernameRecoveryLookupPort")
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
            .as("Username Recovery lookup outbound port must stay framework-free and adapter-free");
    rule.check(classes);
  }

  @Test
  void lookupPortDependencySurfaceIsJavaReactorAndApplicationDtosOnly() {
    var classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.bct.ngtpa.apiservice.application.port.out");

    ArchRule rule =
        classes()
            .that()
            .resideInAPackage("..application.port.out..")
            .and()
            .haveSimpleName("UsernameRecoveryLookupPort")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("java..", "reactor..", "com.bct.ngtpa.apiservice.application.dto..")
            .as(
                "Username Recovery lookup outbound port may depend only on Java, Reactor, and application DTOs");
    rule.check(classes);
  }

  @Test
  void lookupPortIsImplementedOnlyByTheApimAdapter() {
    var classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.bct.ngtpa.apiservice");

    ArchRule rule =
        noClasses()
            .that()
            .resideOutsideOfPackage("..adapter.out.apim..")
            .should()
            .implement(UsernameRecoveryLookupPort.class)
            .as("Only the APIM adapter may implement the Username Recovery lookup port");
    rule.check(classes);
  }

  @Test
  void trpSearchMemberApimDtosNeverLeaveTheApimAdapter() {
    var classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.bct.ngtpa.apiservice");

    ArchRule rule =
        noClasses()
            .that()
            .resideOutsideOfPackage("..adapter.out.apim..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..adapter.out.apim.usernamerecovery.dto..")
            .as("TRPSearchMember APIM DTOs must never be used outside adapter/out/apim");
    rule.check(classes);
  }

  @Test
  void usernameRecoveryLookupBoundaryNeverReferencesApiStatus() {
    var classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.bct.ngtpa.apiservice");

    ArchRule rule =
        noClasses()
            .that()
            .resideInAnyPackage(
                "..application.port.out..", "..application.dto.auth.usernamerecovery..")
            .should()
            .dependOnClassesThat()
            .haveSimpleName("ApiStatus")
            .as(
                "Username Recovery lookup boundary must never reference ApiStatus so DOWNSTREAM_REJECTED never becomes public");
    rule.check(classes);
  }

  @Test
  void usernameRecoveryLookupDtosStayFrameworkFreeRecords() {
    var classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery");

    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..application.dto.auth.usernamerecovery..")
            .and()
            .haveSimpleNameStartingWith("UsernameRecoveryLookup")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "reactor..",
                "com.bct.ngtpa.apiservice.adapter..",
                "com.bct.ngtpa.apiservice.config..",
                "com.bct.ngtpa.apiservice.infrastructure..",
                "io.lettuce..",
                "org.springframework.data.redis..",
                "org.springframework.web..",
                "org.springframework.http..")
            .as("Username Recovery lookup DTOs must remain framework-free records");
    rule.check(classes);
  }
}
