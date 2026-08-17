package com.bct.ngtpa.apiservice.application.usecase.auth.login.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.auth.login.account.LoginAccountRef;
import com.bct.ngtpa.apiservice.application.dto.auth.login.account.LoginSessionAccount;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class EstablishAuthenticatedLoginSessionContractTest {
    @Test
    void commandCarriesVerifiedActorAccountsPreAuthIdentityAndCsrfHash() {
        var command = new EstablishAuthenticatedLoginSessionCommand(
                new AuthFlowSessionId("afs_opaque"),
                new ActorContext("member01", ActorUserRole.ROLE_USER_MBR),
                List.of(account()),
                "csrf_hash",
                "req-1");

        assertThat(command.accounts()).hasSize(1);
        assertThat(command.toString())
                .contains("accountCount=1")
                .doesNotContain("member01", "00000008802", "0001", "csrf_hash", "afs_opaque");
    }

    @Test
    void commandRejectsMissingActorAccountsAndCsrfHash() {
        assertThatThrownBy(() -> new EstablishAuthenticatedLoginSessionCommand(
                new AuthFlowSessionId("afs_opaque"), null, List.of(account()), "csrf_hash", "req"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EstablishAuthenticatedLoginSessionCommand(
                        new AuthFlowSessionId("afs_opaque"), new ActorContext("member01", ActorUserRole.ROLE_USER_MBR), List.of(), "csrf_hash", "req"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EstablishAuthenticatedLoginSessionCommand(
                        new AuthFlowSessionId("afs_opaque"), new ActorContext("member01", ActorUserRole.ROLE_USER_MBR), List.of(account()), " ", "req"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void accountMapperDropsRawIdentifiersAndPreservesPublicAndSessionFields() {
        var mapped = LoginAuthenticatedSessionAccountMapper.map(List.of(account()));
        assertThat(mapped).hasSize(1);
        assertThat(mapped.getFirst().accountRef().value()).isEqualTo("acc_abcdefghijklmnopqrstuvwx");
        assertThat(mapped.getFirst().policyNo()).isEqualTo("00000008802");
        assertThat(mapped.getFirst().certNo()).isEqualTo("0001");
        assertThat(mapped.toString()).doesNotContain("8802", "1");
    }

    @Test
    void successResultExposesOpaqueSessionIdentityOnly() {
        var result = EstablishAuthenticatedLoginSessionResult.established(new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx"), "authenticated_csrf_token");
        assertThat(result.status()).isEqualTo(EstablishAuthenticatedLoginSessionStatus.ESTABLISHED);
        assertThat(result.authenticatedSessionId()).isNotNull();
        assertThat(result.clearAuthFlowCookie()).isTrue();
        assertThat(result.toString()).doesNotContain("sid_abcdefghijklmnopqrstuvwx");
    }

    private static LoginSessionAccount account() {
        return new LoginSessionAccount(
                new LoginAccountRef("acc_abcdefghijklmnopqrstuvwx"),
                "DB", "8802", "00000008802", "1", "0001", "DBA", "OE", "T",
                LocalDate.of(2026, 7, 20));
    }
}
