package com.bct.ngtpa.apiservice.adapter.in.web.auth.login.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.resend.*;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import org.junit.jupiter.api.Test;

class LoginOtpResendWebMapperTest {
    private final LoginOtpResendWebMapper m = new LoginOtpResendWebMapper();

    @Test
    void mapsSuccessReplayAndCooldownToVerifyOtp() {
        for (var s : java.util.List.of(ResendLoginOtpStatus.RESENT, ResendLoginOtpStatus.COMPLETED_REPLAY,
                ResendLoginOtpStatus.COOLDOWN_ACTIVE)) {
            var r = m.toMutationResponse(new ResendLoginOtpResult(s, "****4567", 300, 60,
                    s == ResendLoginOtpStatus.COOLDOWN_ACTIVE ? 25 : null));
            assertThat(r.result().nextStep()).isEqualTo(WorkflowNextStep.VERIFY_OTP);
        }
    }
}
