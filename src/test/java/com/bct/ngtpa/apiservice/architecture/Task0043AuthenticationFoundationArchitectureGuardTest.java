package com.bct.ngtpa.apiservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Task 0043 architectural baseline. Future login/recovery code must consume the
 * shared OTP/APIM/session foundations instead of creating parallel stacks.
 */
@AnalyzeClasses(packages = "com.bct.ngtpa.apiservice", importOptions = ImportOption.DoNotIncludeTests.class)
class Task0043AuthenticationFoundationArchitectureGuardTest {

        @ArchTest
        static final ArchRule applicationMustRemainIndependentOfInboundWeb = noClasses()
                        .that().resideInAPackage("..application..")
                        .should().dependOnClassesThat().resideInAPackage("..adapter.in.web..")
                        .as("application code must not depend on inbound web adapters");

        @ArchTest
        static final ArchRule applicationPortsMustRemainIndependentOfOutboundImplementations = noClasses()
                        .that().resideInAPackage("..application.port..")
                        .should().dependOnClassesThat().resideInAnyPackage(
                                        "..adapter.out.redis..",
                                        "..adapter.out.apim..")
                        .as("application ports must not depend on Redis or APIM implementations");

        @ArchTest
        static final ArchRule loginMustNotOwnWebClientInfrastructure = noClasses()
                        .that().resideInAnyPackage("..login..", "..forgotpassword..", "..usernamerecovery..")
                        .should().dependOnClassesThat().resideInAnyPackage(
                                        "org.springframework.web.reactive.function.client..",
                                        "..adapter.out.apim.oauth..",
                                        "..adapter.out.apim.certificate..",
                                        "..adapter.out.apim.crypto..")
                        .allowEmptyShould(true)
                        .as("future auth workflows must reuse shared APIM infrastructure");

        @ArchTest
        static final ArchRule loginMustNotOwnOtpPersistenceInfrastructure = noClasses()
                        .that().resideInAnyPackage(
                                        "..application..login..",
                                        "..application..forgotpassword..",
                                        "..application..usernamerecovery..",
                                        "..adapter.in..login..",
                                        "..adapter.in..forgotpassword..",
                                        "..adapter.in..usernamerecovery..")
                        .should().dependOnClassesThat().resideInAPackage("..adapter.out.redis..otp..")
                        .allowEmptyShould(true)
                        .as("login workflow and inbound layers must use shared OTP ports and must not depend on Redis OTP infrastructure; infrastructure composition remains allowed");

        @ArchTest
        static final ArchRule authFlowSessionMustNotDependOnPortalContextAdapters = noClasses()
                        .that().haveSimpleNameContaining("AuthFlowSession")
                        .should().dependOnClassesThat().resideInAnyPackage(
                                        "..adapter.in.web.filter..",
                                        "..adapter.out.context..")
                        .as("pre-auth AuthFlowSession must remain independent of portal access resolution");
}
