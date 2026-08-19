package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Stage 010 fail-first contract for authenticated TRPGetMemberInfo retrieval.
 *
 * <p>This source-oriented guard deliberately precedes the production migration. Direct
 * executor-backed behavioral tests supplement it during implementation.
 */
class PersonalInformationRetrievalAuthenticatedExecutorBoundaryTest {

  private static final Path MAIN = Path.of("src/main/java/com/bct/ngtpa/apiservice");

  @Test
  void controllerCopiesTrustedActorAndSelectedAccountIntoRetrievalCommand() throws Exception {
    String controller = source("adapter/in/web/controller/PersonalInformationController.java");
    String command = source("application/dto/GetPersonalInformationCommand.java");

    assertThat(controller)
        .containsIgnoringWhitespaces(
            "currentPortalAccessContextResolver.current()",
            "portalAccessContext.account().policyNo()",
            "portalAccessContext.account().certNo()",
            "portalAccessContext.actor().actorUserId()",
            "portalAccessContext.actor().actorUserRole()",
            "new GetPersonalInformationCommand(");
    assertThat(command)
        .containsIgnoringWhitespaces("ActorUserRole actorUserRole")
        .doesNotContain("accountRef");
  }

  @Test
  void servicePassesExplicitFocusedCommandWithoutAmbientContext() throws Exception {
    String service = source("application/usecase/GetPersonalInformationService.java");
    String focused = source("application/dto/FetchMemberInfoCommand.java");

    assertThat(service)
        .containsIgnoringWhitespaces(
            "command.actorUserRole()", "new FetchMemberInfoCommand(", "fetchMemberInfo(")
        .doesNotContain(
            "CurrentPortalAccessContextResolver", "PortalAccessContext", "deferContextual");
    assertThat(focused)
        .containsIgnoringWhitespaces("ActorUserRole actorUserRole")
        .doesNotContain("accountRef", "PortalAccessContext");
  }

  @Test
  void adapterUsesExactTypedAuthenticatedExecutorRequest() throws Exception {
    String adapter = source("adapter/out/apim/ApimMemberInfoAdapter.java");

    assertThat(adapter)
        .containsIgnoringWhitespaces(
            "ApimExchangeExecutor",
            "ApimExchangeRequest",
            "postEnvelope(",
            "\"/ws/NGTPA/v1/TRPGetMemberInfo\"",
            "GetMemberInfoApimRequest.class",
            "GetMemberInfoDataItem.class",
            "ApimDatabaseScope.ACCOUNT_ENV",
            "command.getActorUserRole()",
            "command.getAccountEnv()",
            "ApimRetryEligibility.SAFE_BUSINESS_CALL",
            "ApimResponseValidator.requireSuccessData(response)")
        .doesNotContain(
            "ApimWebClientFacade",
            "ApimCertificateService",
            "ApimPayloadCryptoService",
            "ApimCredentialProfileResolver",
            "ApimProperties",
            "resolveProfile(",
            "getPublicKey(",
            "StringUtils.hasText",
            "ApimDatabaseScope.NON_ACCOUNT",
            "ActorUserRole.ROLE_USER_MBR");
  }

  @Test
  void routingLedgerRecordsAuthenticatedExecutorOwnership() throws Exception {
    String ledger = testSource("adapter/out/apim/ApimRoutingLedgerCharacterizationTest.java");
    assertThat(ledger)
        .containsIgnoringWhitespaces(
            "TRPGetMemberInfo",
            "ACCOUNT_ENV",
            "command.getActorUserRole()",
            "command.getAccountEnv()",
            "SAFE_BUSINESS_CALL",
            "EXECUTOR")
        .doesNotContain("e(\"TRPGetMemberInfo\", \"ApimMemberInfoAdapter.java\", \"INFERRED\"");
  }

  @Test
  void personalInformationUpdateProductionRemainsStage011Owned() throws Exception {
    String update = source("adapter/out/apim/ApimUpdatePersonalInformationAdapter.java");
    assertThat(update).containsIgnoringWhitespaces("TRPUpdMemberInfo", "IDEMPOTENT_MUTATION");
  }

  private String source(String relative) throws Exception {
    return Files.readString(MAIN.resolve(relative));
  }

  private String testSource(String relative) throws Exception {
    return Files.readString(Path.of("src/test/java/com/bct/ngtpa/apiservice").resolve(relative));
  }
}
