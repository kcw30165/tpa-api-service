package com.bct.ngtpa.apiservice.application.dto.session.authflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationSelectedTrust;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class RegistrationUsernameCheckSessionModelTest {

    @Test
    void usernameCheckSessionModelShapeContainsOnlyApprovedFields() {
        var checkedAt = Instant.parse("2026-07-10T00:00:00Z");
        var state = new RegistrationUsernameCheck(
                new RegistrationSelectedTrust("TL", "ENV1"),
                "abc1@",
                checkedAt,
                null);

        assertThat(componentNames(RegistrationUsernameCheck.class))
                .containsExactly("selectedTrust", "username", "checkedAt", "expiresAt")
                .doesNotContain("password", "confirmPassword", "policyNo", "certNo");
        assertThat(componentNames(RegistrationSelectedTrust.class))
                .containsExactly("trustCode", "accountEnv")
                .doesNotContain("policyNo", "certNo");
        assertThat(state.selectedTrust().trustCode()).isEqualTo("TL");
        assertThat(state.selectedTrust().accountEnv()).isEqualTo("ENV1");
        assertThat(state.username()).isEqualTo("abc1@");
        assertThat(state.checkedAt()).isEqualTo(checkedAt);
        assertThat(state.expiresAt()).isNull();
        assertThat(state.toString()).doesNotContain("abc1@");
    }

    @Test
    void authFlowStatusSupportsUsernameCheckBoundaryStates() {
        assertThat(AuthFlowStatus.values())
                .contains(AuthFlowStatus.TRUST_SELECTION_READY,
                        AuthFlowStatus.USERNAME_VERIFIED,
                        AuthFlowStatus.COMPLETING);
    }

    @Test
    void legacyUsernameCheckStateAllowsNullableExpiresAtAndTwoFieldSelectedTrust() {
        var checkedAt = Instant.parse("2026-07-10T00:00:00Z");
        var state = new UsernameCheckState(new SelectedTrust("TL", "ENV1"), "abc1@", checkedAt, null);

        assertThat(componentNames(SelectedTrust.class))
                .containsExactly("trustCode", "accountEnv")
                .doesNotContain("policyNo", "certNo", "schemeName", "logoKey", "displayOrder");
        assertThat(state.expiresAt()).isNull();
        assertThat(state.toString()).doesNotContain("abc1@");
    }

    private static java.util.List<String> componentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }
}
