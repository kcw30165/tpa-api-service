package com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.otp.verify;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class LoginOtpVerifyRetentionPropertiesTest {
    @Test
    void approvedDefaultsValidate() {
        var p = new LoginOtpVerifyRetentionProperties();
        assertThat(p.idempotencyTtl()).hasSeconds(600);
        p.validate();
    }

    @Test
    void proofCannotExpireBeforeIdempotency() {
        var p = new LoginOtpVerifyRetentionProperties();
        p.setOtpConsumptionProofRetentionSeconds(599);
        assertThatThrownBy(p::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void boundsAreEnforced() {
        var p = new LoginOtpVerifyRetentionProperties();
        p.setAuthenticatedLoginHandoffRetentionSeconds(1801);
        assertThatThrownBy(p::validate).isInstanceOf(IllegalStateException.class);
    }
}
