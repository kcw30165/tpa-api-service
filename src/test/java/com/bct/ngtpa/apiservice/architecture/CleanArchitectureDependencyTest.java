package com.bct.ngtpa.apiservice.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.core.domain.JavaClass;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
/**
 * ArchUnit tests enforcing Clean Architecture dependency boundaries.
 *
 * <p>
 * Rules are intentionally static so ArchUnit's JUnit 5 runner evaluates them
 * without a Spring context — keeping the tests fast and free of application
 * startup.
 */
@AnalyzeClasses(packages = "com.bct.ngtpa.apiservice", importOptions = ImportOption.DoNotIncludeTests.class)
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
        // (complementary to rules 1 & 3 — kept explicit for documentation value)

        @ArchTest
        static final ArchRule domainDoesNotImportConfig = noClasses()
                        .that().resideInAPackage("..domain..")
                        .should().dependOnClassesThat().resideInAPackage("..config..")
                        .as("Domain must not import from the config package");

        // ── Rule 7: APIM internal implementation types must not leak outside
        // adapter/out/apim ────
        //
        // Option B (narrowed rule) is used here intentionally.
        // Spring @Configuration classes in config/ may need to wire APIM adapter beans
        // by type
        // (e.g. ApimWebClientFacade, ApimPayloadCryptoService) as legitimate Spring
        // composition.
        // A blanket rule blocking all external references to adapter.out.apim would
        // prevent that.
        // Instead, we target the internal sub-packages that must never be visible to
        // outer layers:
        // - dto : APIM request/response schema POJOs and envelope wrappers
        // - crypto : ApimCryptoException and low-level crypto primitives
        // - credential: APIM credential resolution internals
        // - oauth : APIM token service internals
        // - certificate: certificate header provider internals
        // - client : WebClient exchange filter internals
        // These sub-packages represent implementation details that must stay inside the
        // APIM adapter.

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

        @ArchTest
        static final ArchRule applicationDoesNotDependOnSpring = noClasses()
                        .that().resideInAPackage("..application..")
                        .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                        .as("Application layer must not depend on Spring Framework");

        @ArchTest
        static final ArchRule applicationDoesNotDependOnSharedLogging = noClasses()
                        .that().resideInAPackage("..application..")
                        .should().dependOnClassesThat().resideInAPackage("..shared.logging..")
                        .as("Application layer must not depend on logging annotations");

        @ArchTest
        static final ArchRule configPackageShouldOnlyContainConfigOrProperties = classes()
                        .that().resideInAPackage("com.bct.ngtpa.apiservice.config")
                        .should().haveSimpleNameEndingWith("Config")
                        .orShould().haveSimpleNameEndingWith("Properties")
                        .as("Global config package should only contain composition/configuration classes");

        // ── Guard: Legacy MemberContext types must not be reintroduced ────────────
        // MemberContext*, MemberContextPort, and TemporaryMemberContext* were replaced
        // by PortalAccessContext*, PortalAccessContextPort, and
        // TemporaryPortalAccessContext*.
        // These rules prevent accidental reintroduction.

        @ArchTest
        static final ArchRule legacyMemberContextPortMustNotExist = noClasses()
                        .that().resideInAPackage("..application.port.out..")
                        .should().haveSimpleName("MemberContextPort")
                        .as("MemberContextPort is superseded by PortalAccessContextPort and must not be reintroduced");

        @ArchTest
        static final ArchRule legacyTemporaryMemberContextAdapterMustNotExist = noClasses()
                        .that().resideInAPackage("..adapter.out.security..")
                        .should().haveSimpleNameStartingWith("TemporaryMemberContext")
                        .as("TemporaryMemberContext* was replaced by TemporaryPortalAccessContext* and must not be reintroduced");

        @ArchTest
        static final ArchRule applicationPortsMustNotBePresentationOrMapperPorts = classes()
                        .that().resideInAPackage("..application.port..")
                        .should(notHaveSimpleNameMatching(".*(Web|Page|View|Form|Response|Presenter|Mapper).*"))
                        .as("Application ports must not model web/page/view/form/response/presenter/mapper concerns. "
                                        + "Presentation mapping belongs in adapter/in/web.");

        // Block application layer from depending on presentation concerns
        @ArchTest
        static final ArchRule applicationMustNotDependOnPresentationConcerns = noClasses()
                        .that().resideInAPackage("..application..")
                        .should().dependOnClassesThat().resideInAnyPackage(
                                        "..adapter.in.web..",
                                        "..adapter.in.web.mapper..",
                                        "..adapter.in.web.response..",
                                        "..adapter.in.web.pageconfig..",
                                        "org.springframework.http..",
                                        "org.springframework.web..")
                        .as("Application layer must not depend on web adapters, web mappers, page config, "
                                        + "web response DTOs, or Spring web response types.");

        // Block use cases/services from returning raw response shapes
        @ArchTest
        static final ArchRule applicationUseCaseExecuteMethodsMustNotReturnWebOrGenericResponses = methods()
                        .that().areDeclaredInClassesThat().resideInAnyPackage(
                                        "..application.port.in..",
                                        "..application.usecase..")
                        .and().haveName("execute")
                        .should(notReturnWebOrGenericResponseTypes())
                        .as("Application use case execute methods must return typed application DTO/results, "
                                        + "not Map/Object/web/page/form/HTTP response shapes.");

        // Stronger optional rule: use case results should be *Result
        @ArchTest
        static final ArchRule applicationUseCaseExecuteMethodsShouldReturnResultDtos = methods()
                        .that().areDeclaredInClassesThat().resideInAnyPackage(
                                        "..application.port.in..",
                                        "..application.usecase..")
                        .and().haveName("execute")
                        .should(returnTypedResultDto())
                        .as("Application use case execute methods should return Mono<...Result> or a ...Result DTO. "
                                        + "Web response shaping belongs in adapter/in/web.");

        // Helper condition for the above rule
        private static ArchCondition<JavaMethod> returnTypedResultDto() {
                return new ArchCondition<>("return typed application result DTO") {
                        @Override
                        public void check(JavaMethod method, ConditionEvents events) {
                                String returnTypeName = method.getReturnType().getName();

                                boolean allowed = returnTypeName.contains(".application.dto.")
                                                && returnTypeName.contains("Result");

                                if (!allowed) {
                                        events.add(SimpleConditionEvent.violated(
                                                        method,
                                                        method.getFullName()
                                                                        + " returns "
                                                                        + returnTypeName
                                                                        + ". Expected a typed application result DTO, normally Mono<...Result>."));
                                }
                        }
                };
        }

        private static ArchCondition<JavaMethod> notReturnWebOrGenericResponseTypes() {
                return new ArchCondition<>("not return web or generic response types") {
                        @Override
                        public void check(JavaMethod method, ConditionEvents events) {
                                String returnTypeName = method.getReturnType().getName();

                                boolean invalid = returnTypeName.equals("java.lang.Object")
                                                || returnTypeName.contains("java.lang.Object")
                                                || returnTypeName.equals("java.util.Map")
                                                || returnTypeName.contains("java.util.Map<")
                                                || returnTypeName.contains("java.util.HashMap")
                                                || returnTypeName.contains("java.util.LinkedHashMap")
                                                || returnTypeName.contains("com.fasterxml.jackson.databind.JsonNode")
                                                || returnTypeName.contains("org.springframework.http.ResponseEntity")
                                                || returnTypeName.contains(
                                                                "org.springframework.web.reactive.function.server.ServerResponse")
                                                || returnTypeName.contains(".adapter.in.web.response.")
                                                || returnTypeName.contains(".adapter.in.web.pageconfig.")
                                                || returnTypeName.contains(".adapter.in.web.mapper.");

                                if (invalid) {
                                        events.add(SimpleConditionEvent.violated(
                                                        method,
                                                        method.getFullName()
                                                                        + " returns "
                                                                        + returnTypeName
                                                                        + ". Use a typed application DTO/result and map it to the web response in adapter/in/web."));
                                }
                        }
                };
        }

        private static ArchCondition<JavaClass> notHaveSimpleNameMatching(String regex) {
                return new ArchCondition<>("not have simple name matching " + regex) {
                        @Override
                        public void check(JavaClass javaClass, ConditionEvents events) {
                                if (javaClass.getSimpleName().matches(regex)) {
                                        events.add(SimpleConditionEvent.violated(
                                                        javaClass,
                                                        javaClass.getName()
                                                                        + " has presentation/mapper-style name. "
                                                                        + "Application ports must not be web/page/view/form/response/presenter/mapper ports."));
                                }
                        }
                };
        }
}
