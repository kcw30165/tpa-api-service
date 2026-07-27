package com.bct.ngtpa.apiservice.adapter.in.web.auth.login.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.verify.*;
import org.junit.jupiter.api.Test;

class LoginOtpVerifyWebMapperTest {
    final LoginOtpVerifyWebMapper m = new LoginOtpVerifyWebMapper();

    @Test
    void invalidStaysOnVerifyOtp() {
        var r = m.toMutationResponse(new VerifyLoginOtpResult(VerifyLoginOtpStatus.INVALID, 3, false));
        assertThat(r.result().nextStep()).isEqualTo(WorkflowNextStep.VERIFY_OTP);
        assertThat(r.errors().getFirst().code()).isEqualTo("err.login.otp.invalid");
    }

    @Test
    void exhaustionUsesRestartFlowAction() {
        var r = m.toMutationResponse(new VerifyLoginOtpResult(VerifyLoginOtpStatus.VERIFY_ATTEMPTS_EXCEEDED, 0, true));
        assertThat(r.result().nextStep()).isEqualTo(WorkflowNextStep.RESTART_FLOW);
        assertThat(r.result().data().actions().getFirst().code()).isEqualTo("RESTART_FLOW");
    }
}
