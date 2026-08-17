package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp;

import static org.assertj.core.api.Assertions.*;
import com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.*;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LoginProtectedAuthFlowContextResultCsrfHashTest {
    @Test
    void validResultCarriesOnlyPersistedHashForAuthenticatedSessionHandoff() {
        var c = new LoginPreAuthContext("member", "ROLE_USER_MBR", Map.of(), Instant.EPOCH, "v1");
        var r = LoginProtectedAuthFlowContextResult.valid(c, "otp", null, "csrf_hash");
        assertThat(r.csrfTokenHash()).isEqualTo("csrf_hash");
        assertThat(r.toString()).doesNotContain("csrf_hash");
    }

    @Test
    void validResultRejectsBlankHashAndInvalidResultCarriesNone() {
        var c = new LoginPreAuthContext("member", "ROLE_USER_MBR", Map.of(), Instant.EPOCH, "v1");
        assertThatThrownBy(() -> LoginProtectedAuthFlowContextResult.valid(c, "otp", null, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(LoginProtectedAuthFlowContextResult.invalid(LoginProtectedAuthFlowContextStatus.MISSING_SESSION)
                .csrfTokenHash()).isNull();
    }
}
