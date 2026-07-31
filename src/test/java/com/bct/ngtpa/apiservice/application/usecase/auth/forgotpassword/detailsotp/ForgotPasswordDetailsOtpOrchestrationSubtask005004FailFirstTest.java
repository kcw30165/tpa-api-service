package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import org.junit.jupiter.api.Test;

class ForgotPasswordDetailsOtpOrchestrationSubtask005004FailFirstTest {
    @Test
    void calculatedLeaseIncludesEveryBound() {
        var p = new ForgotPasswordDetailsOtpLeasePolicy(60, 10, 10, 3, 10000, 100, 5000, 1000);
        assertThat(p.minimumLeaseMillis()).isEqualTo(55200);
        assertThat(p.lease()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void tenSecondsRejected() {
        assertThatThrownBy(() -> new ForgotPasswordDetailsOtpLeasePolicy(10, 10, 10, 3, 10000, 100, 5000, 1000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void upperBoundsRejected() {
        assertThatThrownBy(() -> new ForgotPasswordDetailsOtpLeasePolicy(121, 10, 10, 3, 10000, 100, 5000, 1000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ForgotPasswordDetailsOtpLeasePolicy(60, 31, 10, 3, 10000, 100, 5000, 1000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void monotonicBudgetFailsClosed() {
        var b = new ForgotPasswordMutationLeaseBudget(Duration.ofSeconds(60), () -> 0L);
        assertThat(b.hasPersistenceBudget(Duration.ofSeconds(10))).isTrue();
    }
}
