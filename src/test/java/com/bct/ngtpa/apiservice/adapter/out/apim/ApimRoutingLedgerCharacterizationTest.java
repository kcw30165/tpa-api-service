package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Test-owned current-state routing ledger. It records behavior; it does not approve it. */
class ApimRoutingLedgerCharacterizationTest {
  record Entry(
      String operation,
      String file,
      String currentScope,
      String currentRole,
      String accountEnvSource,
      String retry,
      String encryptionOwner,
      String target) {}

  private static final List<Entry> LEDGER =
      List.of(
          e(
              "TRPPwdValidation",
              "passwordvalidation/ApimPasswordValidationAdapter.java",
              "NON_ACCOUNT",
              "command.actorUserRole()",
              "null",
              "NON_RETRYABLE",
              "EXECUTOR",
              "NON_ACCOUNT"),
          e(
              "TRPGetMemberAccBal",
              "accountbalance/ApimAccountBalanceSourceAdapter.java",
              "ACCOUNT_ENV",
              "command.actorUserRole()",
              "command.accountEnv()",
              "SAFE_BUSINESS_CALL",
              "EXECUTOR",
              "ACCOUNT_ENV"),
          e(
              "TRPGetInvestGL",
              "accountgainloss/ApimAccountGainLossSourceAdapter.java",
              "ACCOUNT_ENV",
              "command.actorUserRole()",
              "command.accountEnv()",
              "SAFE_BUSINESS_CALL",
              "EXECUTOR",
              "ACCOUNT_ENV"),
          e(
              "TRPGetContSumy",
              "ApimContributionSummaryAdapter.java",
              "ACCOUNT_ENV",
              "command.actorUserRole()",
              "command.accountEnv()",
              "SAFE_BUSINESS_CALL",
              "EXECUTOR",
              "ACCOUNT_ENV"),
          e(
              "TRPGetMemberInfo",
              "ApimMemberInfoAdapter.java",
              "ACCOUNT_ENV",
              "command.getActorUserRole()",
              "command.getAccountEnv()",
              "SAFE_BUSINESS_CALL",
              "EXECUTOR",
              "ACCOUNT_ENV"),
          e(
              "TRPGetMsgBoard",
              "ApimNoticeMessageAdapter.java",
              "ACCOUNT_ENV",
              "command.mbrType()",
              "command.accountEnv()",
              "SAFE_BUSINESS_CALL",
              "EXECUTOR",
              "ACCOUNT_ENV"),
          e(
              "TRPUpdMsgRead",
              "ApimNotificationReadStatusAdapter.java",
              "ACCOUNT_ENV",
              "command.mbrType()",
              "command.accountEnv()",
              "IDEMPOTENT_MUTATION",
              "EXECUTOR",
              "ACCOUNT_ENV"),
          e(
              "TRPGetCountryList",
              "ApimReferenceDataCountriesAdapter.java",
              "ACCOUNT_ENV",
              "command.actorUserRole()",
              "command.accountEnv()",
              "SAFE_BUSINESS_CALL",
              "EXECUTOR",
              "ACCOUNT_ENV"),
          e(
              "TRPGetWebSysDate",
              "ApimReferenceDateRefreshAdapter.java",
              "ACCOUNT_ENV",
              "command.actorUserRole()",
              "command.accountEnv()",
              "SAFE_BUSINESS_CALL",
              "EXECUTOR",
              "ACCOUNT_ENV"),
          e(
              "TRPUpdMemberInfo",
              "ApimUpdatePersonalInformationAdapter.java",
              "ACCOUNT_ENV",
              "command.userRole()",
              "command.accountEnv()",
              "IDEMPOTENT_MUTATION",
              "EXECUTOR",
              "ACCOUNT_ENV"),
          e(
              "TRPResetPassword",
              "forgotpassword/ApimForgotPasswordCompletionAdapter.java",
              "NON_ACCOUNT",
              "command.actorUserRole()",
              "null",
              "IDEMPOTENT_MUTATION",
              "EXECUTOR",
              "NON_ACCOUNT"),
          e(
              "TRPSearchMemberForgotPassword",
              "forgotpassword/details/ApimForgotPasswordDetailsLookupAdapter.java",
              "NON_ACCOUNT",
              "command.actorUserRole()",
              "null",
              "SAFE_BUSINESS_CALL",
              "EXECUTOR",
              "NON_ACCOUNT"),
          e(
              "TRPMemberLogin",
              "login/SharedApimTrpMemberLoginTransport.java",
              "NON_ACCOUNT",
              "command.actorUserRole()",
              "null",
              "SAFE_BUSINESS_CALL",
              "EXECUTOR",
              "NON_ACCOUNT"),
          e(
              "TRPGetAccountList",
              "login/account/SharedApimTrpGetAccountListTransport.java",
              "NON_ACCOUNT",
              "command.actorUserRole()",
              "null",
              "SAFE_BUSINESS_CALL",
              "EXECUTOR",
              "NON_ACCOUNT"),
          e(
              "TRPRegMember",
              "registration/ApimRegistrationCompletionAdapter.java",
              "ACCOUNT_ENV",
              "command.actorUserRole()",
              "command.accountEnv()",
              "NON_RETRYABLE",
              "EXECUTOR",
              "ACCOUNT_ENV"),
          e(
              "TRPSearchMemberRegistration",
              "registration/ApimRegistrationLookupAdapter.java",
              "NON_ACCOUNT",
              "command.actorUserRole()",
              "null",
              "SAFE_BUSINESS_CALL",
              "EXECUTOR",
              "NON_ACCOUNT"),
          e(
              "TRPChkCustId",
              "registration/SharedApimTrpChkCustIdTransport.java",
              "NON_ACCOUNT",
              "command.actorUserRole()",
              "null",
              "SAFE_BUSINESS_CALL",
              "EXECUTOR",
              "NON_ACCOUNT"),
          e(
              "TRPSearchMember",
              "usernamerecovery/TrpSearchMemberAdapter.java",
              "NON_ACCOUNT",
              "command.actorUserRole()",
              "null",
              "SAFE_BUSINESS_CALL",
              "EXECUTOR",
              "NON_ACCOUNT"),
          e(
              "TRPGetGeneralMetaData",
              "metadata/ApimGeneralMetadataSourceAdapter.java",
              "ACCOUNT_ENV",
              "command.actorUserRole()",
              "command.scope().accountEnv()",
              "SAFE_BUSINESS_CALL",
              "EXECUTOR",
              "ACCOUNT_ENV"),
          e(
              "TRPGetFundPriceMode",
              "fundprice/ApimFundPriceModeSourceAdapter.java",
              "ACCOUNT_ENV",
              "command.actorUserRole()",
              "command.accountEnv()",
              "SAFE_BUSINESS_CALL",
              "EXECUTOR",
              "ACCOUNT_ENV"),
          e(
              "TRPGetFundFactSheet",
              "fundfactsheet/ApimFundFactSheetSourceAdapter.java",
              "ACCOUNT_ENV",
              "command.actorUserRole()",
              "command.accountEnv()",
              "SAFE_BUSINESS_CALL",
              "EXECUTOR",
              "ACCOUNT_ENV"));

  @Test
  void ledgerCoversEveryPromptZeroOperationExactlyOnce() {
    assertThat(LEDGER)
        .extracting(Entry::operation)
        .doesNotHaveDuplicates()
        .hasSize(21)
        .containsExactlyInAnyOrder(
            "TRPPwdValidation",
            "TRPGetInvestGL",
            "TRPGetMemberAccBal",
            "TRPGetContSumy",
            "TRPGetMemberInfo",
            "TRPGetMsgBoard",
            "TRPUpdMsgRead",
            "TRPGetCountryList",
            "TRPGetWebSysDate",
            "TRPUpdMemberInfo",
            "TRPResetPassword",
            "TRPSearchMemberForgotPassword",
            "TRPMemberLogin",
            "TRPGetAccountList",
            "TRPRegMember",
            "TRPSearchMemberRegistration",
            "TRPChkCustId",
            "TRPSearchMember",
            "TRPGetGeneralMetaData",
            "TRPGetFundPriceMode",
            "TRPGetFundFactSheet");
  }

  @Test
  void currentRoutingAndRetryLiteralsRemainPresentInOwningSources() throws Exception {
    for (Entry entry : LEDGER) {
      String source = source(entry.file());
      assertThat(source).as(entry.operation()).contains(entry.currentRole(), entry.retry());
      if (entry.currentScope().equals("NON_ACCOUNT")
          || entry.currentScope().equals("ACCOUNT_ENV")) {
        assertThat(source)
            .as(entry.operation())
            .contains("ApimDatabaseScope." + entry.currentScope());
      }
      if (entry.currentScope().equals("INFERRED")) {
        assertThat(source)
            .as(entry.operation() + " known scope inference")
            .contains("ACCOUNT_ENV", "NON_ACCOUNT", "hasText");
      }
    }
  }

  @Test
  void confirmedTargetsAreSeparatedFromKnownCurrentDrift() {
    assertThat(find("TRPGetCountryList").currentScope()).isEqualTo("ACCOUNT_ENV");
    assertThat(find("TRPGetCountryList").target()).isEqualTo("ACCOUNT_ENV");
    assertThat(find("TRPGetMsgBoard").currentScope()).isEqualTo("ACCOUNT_ENV");
    assertThat(find("TRPGetMsgBoard").target()).isEqualTo("ACCOUNT_ENV");
    assertThat(find("TRPResetPassword").target()).isEqualTo("NON_ACCOUNT");
    assertThat(find("TRPChkCustId").target()).isEqualTo("NON_ACCOUNT");
  }

  private static Entry e(
      String operation,
      String file,
      String scope,
      String role,
      String env,
      String retry,
      String owner,
      String target) {
    return new Entry(operation, file, scope, role, env, retry, owner, target);
  }

  private Entry find(String operation) {
    return LEDGER.stream().filter(e -> e.operation().equals(operation)).findFirst().orElseThrow();
  }

  private String source(String relative) throws Exception {
    return Files.readString(
        Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim").resolve(relative));
  }

  @org.junit.jupiter.api.Test
  void completionRoleOriginRemainsAtTrustedInboundBoundaryAfterStage06Migration() throws Exception {
    String mapper =
        java.nio.file.Files.readString(
            java.nio.file.Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/forgotpassword/mapper/ForgotPasswordCompleteWebMapper.java"));
    String inboundCommand =
        java.nio.file.Files.readString(
            java.nio.file.Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/complete/CompleteForgotPasswordCommand.java"));
    String outboundCommand =
        java.nio.file.Files.readString(
            java.nio.file.Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/complete/ForgotPasswordCompletionCommand.java"));
    String adapter =
        java.nio.file.Files.readString(
            java.nio.file.Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/forgotpassword/ApimForgotPasswordCompletionAdapter.java"));

    org.assertj.core.api.Assertions.assertThat(mapper).contains("ActorUserRole.ROLE_USER_MBR");
    org.assertj.core.api.Assertions.assertThat(inboundCommand)
        .contains("ActorUserRole actorUserRole");
    org.assertj.core.api.Assertions.assertThat(outboundCommand)
        .contains("ActorUserRole actorUserRole");
    org.assertj.core.api.Assertions.assertThat(adapter)
        .contains("command.actorUserRole()", "IDEMPOTENT_MUTATION", "ApimDatabaseScope.NON_ACCOUNT")
        .doesNotContain("ActorUserRole.ROLE_USER_MBR");
  }
}
