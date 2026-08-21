package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Fail-first Stage 008 guard for the two authenticated Notifications APIM operations. */
class NotificationsAuthenticatedExecutorBoundaryTest {
  private static final Path APIM =
      Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim");

  @Test
  void retrievalUsesTypedExecutorWithAuthoritativeAuthenticatedRouting() throws Exception {
    String source = read("ApimNoticeMessageAdapter.java");

    assertThat(source)
        .containsIgnoringWhitespaces(
            "ApimExchangeExecutor",
            "ApimExchangeRequest",
            "postEnvelope(",
            "GetMessageBoardApimRequest.class",
            "GetMessageBoardDataItem.class",
            "ApimDatabaseScope.ACCOUNT_ENV",
            "command.mbrType()",
            "command.accountEnv()",
            "ApimRetryEligibility.SAFE_BUSINESS_CALL")
        .doesNotContain(
            "ApimWebClientFacade",
            "ApimCertificateService",
            "ApimPayloadCryptoService",
            "ApimProperties",
            "resolveProfile(",
            "getPublicKey(",
            "ActorUserRole.ROLE_USER_MBR",
            "ApimDatabaseScope.NON_ACCOUNT");
  }

  @Test
  void updateUsesTypedExecutorWithAuthoritativeAuthenticatedRouting() throws Exception {
    String source = read("ApimNotificationReadStatusAdapter.java");

    assertThat(source)
        .containsIgnoringWhitespaces(
            "ApimExchangeExecutor",
            "ApimExchangeRequest",
            "postEnvelope(",
            "UpdateNotificationReadStatusApimRequest.class",
            "UpdateNotificationReadStatusApimDataItem.class",
            "ApimDatabaseScope.ACCOUNT_ENV",
            "command.mbrType()",
            "command.accountEnv()",
            "ApimRetryEligibility.IDEMPOTENT_MUTATION")
        .doesNotContain(
            "ApimWebClientFacade",
            "ApimCertificateService",
            "ApimPayloadCryptoService",
            "ApimProperties",
            "resolveProfile(",
            "getPublicKey(",
            "ActorUserRole.ROLE_USER_MBR",
            "ApimDatabaseScope.NON_ACCOUNT");
  }

  @Test
  void bothOperationsPreserveFeatureOwnedEnvelopeInterpretation() throws Exception {
    assertThat(read("ApimNoticeMessageAdapter.java"))
        .containsIgnoringWhitespaces(
            "ApimResponseValidator.requireSuccessData(response)",
            "toNotificationListResult",
            "toNoticeMessage",
            "parseDateTime");
    assertThat(read("ApimNotificationReadStatusAdapter.java"))
        .containsIgnoringWhitespaces(
            "ApimResponseValidator.requireSuccessData(response)", "toResult", "resolveMessageCode");
  }

  @Test
  void servicesRemainAmbientContextFreeAndControllerRetainsTrustedContextOwnership()
      throws Exception {
    String getService =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/GetNotificationsService.java"));
    String updateService =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/UpdateNotificationsReadStatusService.java"));
    String controller =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/notification/NotificationController.java"));

    assertThat(getService)
        .doesNotContain("CurrentPortalAccessContextResolver", "PortalAccessContext");
    assertThat(updateService)
        .doesNotContain("CurrentPortalAccessContextResolver", "PortalAccessContext");
    assertThat(controller)
        .containsIgnoringWhitespaces(
            "CurrentPortalAccessContextResolver",
            "currentPortalAccessContextResolver.current()",
            ".account()",
            ".actor()");
  }

  @Test
  void routingLedgerRecordsBothNotificationsOperationsAsExecutorOwned() throws Exception {
    String ledger =
        Files.readString(
            Path.of(
                "src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimRoutingLedgerCharacterizationTest.java"));
    assertThat(ledger)
        .containsIgnoringWhitespaces(
            "e(\"TRPGetMsgBoard\", \"ApimNoticeMessageAdapter.java\", \"ACCOUNT_ENV\", \"command.mbrType()\", \"command.accountEnv()\", \"SAFE_BUSINESS_CALL\", \"EXECUTOR\", \"ACCOUNT_ENV\")")
        .containsIgnoringWhitespaces(
            "e(\"TRPUpdMsgRead\", \"ApimNotificationReadStatusAdapter.java\", \"ACCOUNT_ENV\", \"command.mbrType()\", \"command.accountEnv()\", \"IDEMPOTENT_MUTATION\", \"EXECUTOR\", \"ACCOUNT_ENV\")");
  }

  private static String read(String file) throws Exception {
    return Files.readString(APIM.resolve(file));
  }
}
