package com.bct.ngtpa.apiservice.application.usecase.auth.login.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.login.account.*;
import com.bct.ngtpa.apiservice.application.port.out.LoginAccountRefGeneratorPort;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoginAccountReferenceAssignmentServiceTest {
    @Test
    void assignsOneUniqueOpaqueReferencePerValidatedAccountAndPreservesOrder() {
        var values = new ArrayDeque<>(List.of(
                new LoginAccountRef("acc_first_opaque_reference"),
                new LoginAccountRef("acc_second_opaque_reference")));
        LoginAccountRefGeneratorPort generator = values::removeFirst;
        var service = new LoginAccountReferenceAssignmentService(generator, 8);

        var result = service.assign(List.of(valid("DB", "00000008802", "0001"),
                valid("JP", "00000009901", "0002")));

        assertThat(result.accounts()).extracting(LoginSessionAccount::accountRef)
                .extracting(LoginAccountRef::value)
                .containsExactly("acc_first_opaque_reference", "acc_second_opaque_reference");
        assertThat(result.accounts()).extracting(LoginSessionAccount::accountEnv)
                .containsExactly("DB", "JP");
        assertThat(result.accounts().get(0).rawPolicyNo()).isEqualTo("8802");
        assertThat(result.toString()).doesNotContain(
                "acc_first", "8802", "0001", "DBA");
    }

    @Test
    void retriesGeneratorCollisionsWithoutMergingAccounts() {
        var values = new ArrayDeque<>(List.of(
                new LoginAccountRef("acc_repeated_opaque_reference"),
                new LoginAccountRef("acc_repeated_opaque_reference"),
                new LoginAccountRef("acc_unique_opaque_reference")));
        var result = new LoginAccountReferenceAssignmentService(values::removeFirst, 4)
                .assign(List.of(valid("DB", "00000008802", "0001"),
                        valid("JP", "00000009901", "0002")));
        assertThat(result.accounts()).hasSize(2);
        assertThat(result.accounts().stream().map(a -> a.accountRef().value()).distinct())
                .hasSize(2);
    }

    @Test
    void failsClosedWhenUniquenessCannotBeEstablished() {
        LoginAccountRefGeneratorPort generator =
                () -> new LoginAccountRef("acc_repeated_opaque_reference");
        var service = new LoginAccountReferenceAssignmentService(generator, 2);
        assertThatThrownBy(() -> service.assign(List.of(
                        valid("DB", "00000008802", "0001"),
                        valid("JP", "00000009901", "0002"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unique account reference");
    }

    @Test
    void rejectsEmptyAccountListBecausePortalSessionRequiresAccounts() {
        var service = new LoginAccountReferenceAssignmentService(
                () -> new LoginAccountRef("acc_valid_opaque_reference"), 2);
        assertThatThrownBy(() -> service.assign(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private LoginValidatedAccount valid(String env, String policy, String cert) {
        return new LoginValidatedAccount(env, env + "A", policy.replaceFirst("^0+", ""),
                policy, null, Integer.parseInt(cert), cert, "T", LocalDate.of(2026, 7, 20));
    }
}
