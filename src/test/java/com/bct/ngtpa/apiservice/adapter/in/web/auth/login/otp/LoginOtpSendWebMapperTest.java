package com.bct.ngtpa.apiservice.adapter.in.web.auth.login.otp;

import static org.assertj.core.api.Assertions.assertThat;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.*;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import org.junit.jupiter.api.Test;

class LoginOtpSendWebMapperTest {
    private final LoginOtpSendWebMapper mapper = new LoginOtpSendWebMapper();

    @Test
    void mapsFreshAndReplaySuccessWithoutCsrfOrSession() {
        for (var status : java.util.List.of(SendLoginOtpStatus.SENT, SendLoginOtpStatus.COMPLETED_REPLAY)) {
            var body = mapper.toMutationResponse(new SendLoginOtpResult(status, "****4567", 300, 60, null));
            assertThat(body.success()).isTrue();
            assertThat(body.result().nextStep()).isEqualTo(WorkflowNextStep.VERIFY_OTP);
            assertThat(body.toString()).doesNotContain("csrfToken", "authFlowSessionId", "destinationId", "123456");
        }
    }

    @Test
    void mapsStableFailures() {
        assertThat(mapper.toMutationResponse(result(SendLoginOtpStatus.INVALID_DESTINATION)).errors().getFirst().code())
                .isEqualTo("err.login.otp.destination.invalid");
        assertThat(mapper.toMutationResponse(result(SendLoginOtpStatus.DELIVERY_FAILED)).errors().getFirst().code())
                .isEqualTo("err.login.otp.deliveryFailed");
        assertThat(mapper.toMutationResponse(result(SendLoginOtpStatus.KEY_REUSED)).errors().getFirst().code())
                .isEqualTo("err.workflow.idempotency.keyReused");
    }

    private SendLoginOtpResult result(SendLoginOtpStatus s) {
        return new SendLoginOtpResult(s, null, null, null, null);
    }
}
