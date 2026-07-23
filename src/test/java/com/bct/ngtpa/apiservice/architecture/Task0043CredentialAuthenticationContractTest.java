package com.bct.ngtpa.apiservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationResult;
import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.login.LoginActorReference;
import com.bct.ngtpa.apiservice.application.dto.auth.login.OtpDestination;
import com.bct.ngtpa.apiservice.application.port.out.CredentialAuthenticationPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.AuthenticateLoginCredentialsUseCase;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

@AnalyzeClasses(
        packages = "com.bct.ngtpa.apiservice.application",
        importOptions = ImportOption.DoNotIncludeTests.class)
class Task0043CredentialAuthenticationContractTest {

    @Test
    void statusVocabularyDistinguishesAuthenticationOutcomesWithoutApimDetails() {
        assertThat(CredentialAuthenticationStatus.values()).containsExactly(
                CredentialAuthenticationStatus.AUTHENTICATED,
                CredentialAuthenticationStatus.INVALID_CREDENTIALS,
                CredentialAuthenticationStatus.REJECTED,
                CredentialAuthenticationStatus.UNAVAILABLE);
    }

    @Test
    void commandContainsCredentialsAndRequestContextButNoAccountRef() {
        assertThat(componentNames(CredentialAuthenticationCommand.class)).containsExactly(
                "username", "password", "acceptLanguage", "correlationId");
        assertThat(componentNames(CredentialAuthenticationCommand.class))
                .doesNotContain("accountRef", "sessionId");
    }

    @Test
    void authenticatedResultCarriesOpaqueActorReferenceAndSelectableOtpDestinations() {
        assertThat(componentNames(CredentialAuthenticationResult.class)).containsExactly(
                "status", "actorReference", "otpDestinations", "reasonCode");
        assertThat(componentNames(LoginActorReference.class)).containsExactly("value");
        assertThat(componentNames(OtpDestination.class)).containsExactly(
                "destinationRef", "channel", "maskedDestination");
    }

    @Test
    void useCaseDelegatesToOutboundContractWithoutTransportTypes() {
        CredentialAuthenticationResult expected = new CredentialAuthenticationResult(
                CredentialAuthenticationStatus.INVALID_CREDENTIALS,
                null,
                java.util.List.of(),
                "INVALID_CREDENTIALS");
        CredentialAuthenticationPort port = command -> Mono.just(expected);
        AuthenticateLoginCredentialsUseCase useCase =
                new AuthenticateLoginCredentialsUseCase(port);

        reactor.test.StepVerifier.create(useCase.authenticate(
                        new CredentialAuthenticationCommand("member", "secret", "en", "req-1")))
                .expectNext(expected)
                .verifyComplete();
    }

    private java.util.List<String> componentNames(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }

    @ArchTest
    static final ArchRule loginApplicationContractMustNotDependOnAdapters = noClasses()
            .that().resideInAPackage("..application..auth.login..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..adapter..",
                    "org.springframework..",
                    "com.fasterxml.jackson..")
            .as("login application contracts must remain independent of web and APIM adapters");
}
