package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Test-owned current-state routing ledger. It records behavior; it does not approve it. */
class ApimRoutingLedgerCharacterizationTest {
    record Entry(String operation, String file, String currentScope, String currentRole,
            String accountEnvSource, String retry, String encryptionOwner, String target) {}

    private static final List<Entry> LEDGER = List.of(
        e("TRPGetContSumy", "ApimContributionSummaryAdapter.java", "NON_ACCOUNT", "ROLE_USER_MBR", "null", "SAFE_BUSINESS_CALL", "FEATURE", "UNCONFIRMED"),
        e("TRPGetMemberInfo", "ApimMemberInfoAdapter.java", "INFERRED", "ROLE_USER_MBR", "command.getAccountEnv()", "SAFE_BUSINESS_CALL", "FEATURE", "UNCONFIRMED"),
        e("TRPGetMsgBoard", "ApimNoticeMessageAdapter.java", "INFERRED", "ROLE_USER_MBR", "command.accountEnv()", "SAFE_BUSINESS_CALL", "FEATURE", "ACCOUNT_ENV"),
        e("TRPUpdMsgRead", "ApimNotificationReadStatusAdapter.java", "ACCOUNT_ENV", "ROLE_USER_MBR", "command.accountEnv()", "IDEMPOTENT_MUTATION", "FEATURE", "ACCOUNT_ENV"),
        e("TRPGetCountryList", "ApimReferenceDataCountriesAdapter.java", "NON_ACCOUNT", "ROLE_USER_MBR", "null", "SAFE_BUSINESS_CALL", "FACADE", "ACCOUNT_ENV"),
        e("TRPGetWebSysDate", "ApimReferenceDateRefreshAdapter.java", "INFERRED", "ROLE_USER_MBR", "accountEnv", "SAFE_BUSINESS_CALL", "FEATURE", "UNCONFIRMED"),
        e("TRPUpdMemberInfo", "ApimUpdatePersonalInformationAdapter.java", "ACCOUNT_ENV", "ROLE_USER_MBR", "command.accountEnv()", "IDEMPOTENT_MUTATION", "FEATURE", "UNCONFIRMED"),
        e("TRPResetPassword", "forgotpassword/ApimForgotPasswordCompletionAdapter.java", "MIXED", "ROLE_USER_MBR", "protected-or-null", "IDEMPOTENT_MUTATION", "FEATURE", "NON_ACCOUNT"),
        e("TRPSearchRegedMember", "forgotpassword/details/SharedApimTrpSearchRegedMemberTransport.java", "NON_ACCOUNT", "ROLE_USER_MBR", "null", "SAFE_BUSINESS_CALL", "FEATURE", "UNCONFIRMED"),
        e("TRPMemberLogin", "login/SharedApimTrpMemberLoginTransport.java", "NON_ACCOUNT", "ROLE_USER_MBR", "null", "SAFE_BUSINESS_CALL", "MIXED", "UNCONFIRMED"),
        e("TRPGetAccountList", "login/account/SharedApimTrpGetAccountListTransport.java", "NON_ACCOUNT", "ROLE_USER_MBR", "null", "SAFE_BUSINESS_CALL", "MIXED", "UNCONFIRMED"),
        e("TRPRegMember", "registration/ApimRegistrationCompletionAdapter.java", "ACCOUNT_ENV", "ROLE_USER_MBR", "protected accountEnv", "IDEMPOTENT_MUTATION", "FEATURE", "ACCOUNT_ENV"),
        e("TRPSearchUnregMember", "registration/ApimRegistrationLookupAdapter.java", "NON_ACCOUNT", "ROLE_USER_MBR", "null", "SAFE_BUSINESS_CALL", "FEATURE", "UNCONFIRMED"),
        e("TRPChkCustId", "registration/SharedApimTrpChkCustIdTransport.java", "MIXED", "ROLE_USER_MBR", "caller-or-null", "SAFE_BUSINESS_CALL", "MIXED", "FORGOT_PASSWORD_NON_ACCOUNT"),
        e("TRPSearchMember", "usernamerecovery/SharedApimTrpSearchMemberTransport.java", "NON_ACCOUNT", "ROLE_USER_MBR", "null", "SAFE_BUSINESS_CALL", "FEATURE", "UNCONFIRMED")
    );

    @Test
    void ledgerCoversEveryPromptZeroOperationExactlyOnce() {
        assertThat(LEDGER).extracting(Entry::operation).doesNotHaveDuplicates().hasSize(15)
                .containsExactlyInAnyOrder("TRPGetContSumy", "TRPGetMemberInfo", "TRPGetMsgBoard",
                        "TRPUpdMsgRead", "TRPGetCountryList", "TRPGetWebSysDate", "TRPUpdMemberInfo",
                        "TRPResetPassword", "TRPSearchRegedMember", "TRPMemberLogin",
                        "TRPGetAccountList", "TRPRegMember", "TRPSearchUnregMember",
                        "TRPChkCustId", "TRPSearchMember");
    }

    @Test
    void currentRoutingAndRetryLiteralsRemainPresentInOwningSources() throws Exception {
        for (Entry entry : LEDGER) {
            String source = source(entry.file());
            assertThat(source).as(entry.operation()).contains(entry.currentRole(), entry.retry());
            if (entry.currentScope().equals("NON_ACCOUNT") || entry.currentScope().equals("ACCOUNT_ENV")) {
                assertThat(source).as(entry.operation()).contains("ApimDatabaseScope." + entry.currentScope());
            }
            if (entry.currentScope().equals("INFERRED")) {
                assertThat(source).as(entry.operation() + " known scope inference")
                        .contains("ACCOUNT_ENV", "NON_ACCOUNT", "hasText");
            }
        }
    }

    @Test
    void confirmedTargetsAreSeparatedFromKnownCurrentDrift() {
        assertThat(find("TRPGetCountryList").currentScope()).isEqualTo("NON_ACCOUNT");
        assertThat(find("TRPGetCountryList").target()).isEqualTo("ACCOUNT_ENV");
        assertThat(find("TRPGetMsgBoard").currentScope()).isEqualTo("INFERRED");
        assertThat(find("TRPGetMsgBoard").target()).isEqualTo("ACCOUNT_ENV");
        assertThat(find("TRPResetPassword").target()).isEqualTo("NON_ACCOUNT");
        assertThat(find("TRPChkCustId").target()).isEqualTo("FORGOT_PASSWORD_NON_ACCOUNT");
    }

    private static Entry e(String operation, String file, String scope, String role,
            String env, String retry, String owner, String target) {
        return new Entry(operation, file, scope, role, env, retry, owner, target);
    }
    private Entry find(String operation) { return LEDGER.stream().filter(e -> e.operation().equals(operation)).findFirst().orElseThrow(); }
    private String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim").resolve(relative));
    }
}
