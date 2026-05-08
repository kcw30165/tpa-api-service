package com.bct.ngtpa.apiservice.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit tests enforcing Clean Architecture dependency boundaries.
 *
 * <p>Rules are intentionally static so ArchUnit's JUnit 5 runner evaluates them without
 * a Spring context — keeping the tests fast and free of application startup.
 */
@AnalyzeClasses(
        packages = "com.bct.ngtpa.apiservice",
        importOptions = ImportOption.DoNotIncludeTests.class)
class CleanArchitectureDependencyTest {

    // ── Rule 1: Domain must be framework-free and layer-independent ───────────

    @ArchTest
    static final ArchRule domainDoesNotDependOnAdapters = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..")
            .as("Domain must not depend on any adapter package");

    @ArchTest
    static final ArchRule domainDoesNotDependOnApplication = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..application..")
            .as("Domain must not depend on the application layer");

    @ArchTest
    static final ArchRule domainDoesNotDependOnConfig = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..config..")
            .as("Domain must not depend on the config layer");

    @ArchTest
    static final ArchRule domainDoesNotDependOnSpring = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework..")
            .as("Domain must not depend on Spring Framework");

    // ── Rule 2 & 3: Application must not depend on adapters or config ─────────

    @ArchTest
    static final ArchRule applicationDoesNotDependOnAdapters = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..")
            .as("Application layer must not depend on any adapter package");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnConfig = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..config..")
            .as("Application layer must not depend on the config package");

    // ── Rule 4: Inbound adapters must not depend on outbound adapters ─────────

    @ArchTest
    static final ArchRule inboundAdapterDoesNotDependOnOutboundAdapter = noClasses()
            .that().resideInAPackage("..adapter.in..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.out..")
            .as("adapter/in must not depend on adapter/out");

    // ── Rule 6: Domain and application must not depend on config ──────────────
    //    (complementary to rules 1 & 3 — kept explicit for documentation value)

    @ArchTest
    static final ArchRule domainDoesNotImportConfig = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..config..")
            .as("Domain must not import from the config package");

    // ── Rule 7: APIM internal implementation types must not leak outside adapter/out/apim ────
    //
    // Option B (narrowed rule) is used here intentionally.
    // Spring @Configuration classes in config/ may need to wire APIM adapter beans by type
    // (e.g. ApimWebClientFacade, ApimPayloadCryptoService) as legitimate Spring composition.
    // A blanket rule blocking all external references to adapter.out.apim would prevent that.
    // Instead, we target the internal sub-packages that must never be visible to outer layers:
    //   - dto      : APIM request/response schema POJOs and envelope wrappers
    //   - crypto   : ApimCryptoException and low-level crypto primitives
    //   - credential: APIM credential resolution internals
    //   - oauth    : APIM token service internals
    //   - certificate: certificate header provider internals
    //   - client   : WebClient exchange filter internals
    // These sub-packages represent implementation details that must stay inside the APIM adapter.

    @ArchTest
    static final ArchRule apimDtoTypesDoNotLeakOutsideApimAdapter = noClasses()
            .that().resideOutsideOfPackage("..adapter.out.apim..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.out.apim.dto..")
            .as("APIM DTO / envelope types must not be used outside adapter/out/apim");

    @ArchTest
    static final ArchRule apimCryptoTypesDoNotLeakOutsideApimAdapter = noClasses()
            .that().resideOutsideOfPackage("..adapter.out.apim..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.out.apim.crypto..")
            .as("APIM crypto types (including ApimCryptoException) must not be used outside adapter/out/apim");

    @ArchTest
    static final ArchRule apimCredentialTypesDoNotLeakOutsideApimAdapter = noClasses()
            .that().resideOutsideOfPackage("..adapter.out.apim..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.out.apim.credential..")
            .as("APIM credential resolution internals must not be used outside adapter/out/apim");

    @ArchTest
    static final ArchRule apimOAuthTypesDoNotLeakOutsideApimAdapter = noClasses()
            .that().resideOutsideOfPackage("..adapter.out.apim..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.out.apim.oauth..")
            .as("APIM OAuth/token service internals must not be used outside adapter/out/apim");

    @ArchTest
    static final ArchRule apimCertificateTypesDoNotLeakOutsideApimAdapter = noClasses()
            .that().resideOutsideOfPackage("..adapter.out.apim..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.out.apim.certificate..")
            .as("APIM certificate header provider internals must not be used outside adapter/out/apim");

    @ArchTest
    static final ArchRule apimClientTypesDoNotLeakOutsideApimAdapter = noClasses()
            .that().resideOutsideOfPackage("..adapter.out.apim..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.out.apim.client..")
            .as("APIM WebClient exchange filter internals must not be used outside adapter/out/apim");
}
