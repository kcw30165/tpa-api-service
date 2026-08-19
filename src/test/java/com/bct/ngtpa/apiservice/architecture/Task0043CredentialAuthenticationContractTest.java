package com.bct.ngtpa.apiservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationResult;
import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.login.LoginCredentialDestination;
import com.bct.ngtpa.apiservice.application.port.out.CredentialAuthenticationPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.AuthenticateLoginCredentialsUseCase;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@AnalyzeClasses(
    packages = "com.bct.ngtpa.apiservice.application",
    importOptions = ImportOption.DoNotIncludeTests.class)
class Task0043CredentialAuthenticationContractTest {

  @Test
  void canonicalOutcomeVocabularyMatchesConfirmedCredentialScenarios() {
    assertThat(CredentialAuthenticationStatus.values())
        .containsExactly(
            CredentialAuthenticationStatus.AUTHENTICATED,
            CredentialAuthenticationStatus.INCORRECT_PASSWORD,
            CredentialAuthenticationStatus.INCORRECT_USERNAME,
            CredentialAuthenticationStatus.INVALID_ACCOUNT,
            CredentialAuthenticationStatus.ACCOUNT_LOCKED,
            CredentialAuthenticationStatus.UNAVAILABLE);
  }

  @Test
  void commandCarriesExactCredentialsAndRequestContextWithoutAccountOrSession() {
    assertThat(componentNames(CredentialAuthenticationCommand.class))
        .containsExactly(
            "username", "password", "acceptLanguage", "correlationId", "actorUserRole");
    assertThat(componentNames(CredentialAuthenticationCommand.class))
        .doesNotContain("accountRef", "sessionId");
  }

  @Test
  void successResultCarriesProtectedPreAuthInputsWithoutTransportDtos() {
    assertThat(componentNames(CredentialAuthenticationResult.class))
        .containsExactly(
            "status",
            "normalizedUsername",
            "userRole",
            "failCount",
            "destinations",
            "downstreamCode");
    assertThat(componentNames(LoginCredentialDestination.class))
        .containsExactly("channel", "destination");
  }

  @Test
  void useCaseDelegatesThroughApplicationBoundary() {
    CredentialAuthenticationResult expected =
        new CredentialAuthenticationResult(
            CredentialAuthenticationStatus.INCORRECT_PASSWORD, null, null, 1, List.of(), null);
    CredentialAuthenticationPort port = command -> Mono.just(expected);
    var useCase = new AuthenticateLoginCredentialsUseCase(port);

    StepVerifier.create(
            useCase.authenticate(
                new CredentialAuthenticationCommand(
                    "member",
                    "Exact",
                    "en",
                    "req-1",
                    com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR)))
        .expectNext(expected)
        .verifyComplete();
  }

  private List<String> componentNames(Class<?> recordType) {
    return Arrays.stream(recordType.getRecordComponents()).map(RecordComponent::getName).toList();
  }

  @ArchTest
  static final ArchRule loginApplicationContractMustNotDependOnAdapters =
      noClasses()
          .that()
          .resideInAPackage("..application..auth.login..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..adapter..", "org.springframework..", "com.fasterxml.jackson..")
          .as("login application contracts must remain independent of web and APIM adapters");
}
