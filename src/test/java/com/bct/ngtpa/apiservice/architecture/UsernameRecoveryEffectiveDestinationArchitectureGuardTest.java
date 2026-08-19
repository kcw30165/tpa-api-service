package com.bct.ngtpa.apiservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryEffectiveDestinationPort;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Architecture guard for the repair 020A Username Recovery effective destination boundary.
 *
 * <p>Mirrors the Task 018 lookup outbound-port guard. Verifies the effective destination port and
 * DTOs stay framework-free and adapter-free, the port may depend only on Java, Reactor, application
 * DTOs, and domain, and that only the config-layer adapter implements the port. Guard descriptions
 * never reference destination values.
 */
class UsernameRecoveryEffectiveDestinationArchitectureGuardTest {

  @Test
  void portStaysFrameworkAdapterAndInfrastructureFree() {
    var classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.bct.ngtpa.apiservice.application.port.out");

    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..application.port.out..")
            .and()
            .haveSimpleName("UsernameRecoveryEffectiveDestinationPort")
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
            .as(
                "Username Recovery effective destination port must stay framework-free and adapter-free");
    rule.check(classes);
  }

  @Test
  void portDependencySurfaceIsJavaReactorApplicationDtosAndDomainOnly() {
    var classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.bct.ngtpa.apiservice.application.port.out");

    ArchRule rule =
        classes()
            .that()
            .resideInAPackage("..application.port.out..")
            .and()
            .haveSimpleName("UsernameRecoveryEffectiveDestinationPort")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "java..",
                "reactor..",
                "com.bct.ngtpa.apiservice.application.dto..",
                "com.bct.ngtpa.apiservice.domain..")
            .as(
                "Username Recovery effective destination port may depend only on Java, Reactor, application DTOs, and domain");
    rule.check(classes);
  }

  @Test
  void effectiveDestinationDtosStayFrameworkFreeRecords() {
    var classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery");

    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..application.dto.auth.usernamerecovery..")
            .and()
            .haveSimpleNameStartingWith("UsernameRecoveryEffectiveDestination")
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
            .as("Username Recovery effective destination DTOs must remain framework-free records");
    rule.check(classes);
  }

  @Test
  void portIsImplementedOnlyByTheConfigLayerAdapter() {
    var classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.bct.ngtpa.apiservice");

    ArchRule rule =
        noClasses()
            .that()
            .resideOutsideOfPackage("..config.message..")
            .should()
            .implement(UsernameRecoveryEffectiveDestinationPort.class)
            .as(
                "Only the config-layer adapter may implement the Username Recovery effective destination port");
    rule.check(classes);
  }
}
