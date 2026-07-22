package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RegistrationHttpResponsesTest {
    @Test void mapsPreciseFailureStatuses() {
        assertStatus("err.registration.start.request.invalid", ApiStatus.VALIDATION_FAILED, 400);
        assertStatus("err.registration.csrf.invalid", ApiStatus.VALIDATION_FAILED, 403);
        assertStatus("err.registration.state.invalid", ApiStatus.BUSINESS_REJECTED, 409);
        assertStatus("err.registration.otp.invalid", ApiStatus.BUSINESS_REJECTED, 422);
        assertStatus("err.registration.otp.locked", ApiStatus.BUSINESS_REJECTED, 429);
        assertStatus("err.registration.otp.cooldownActive", ApiStatus.BUSINESS_REJECTED, 429);
        assertStatus("err.workflow.downstream.unavailable", ApiStatus.DOWNSTREAM_ERROR, 502);
        assertStatus("err.workflow.system.unavailable", ApiStatus.SYSTEM_ERROR, 503);
    }

    @Test void successfulIdempotentCleanupRemainsOk() {
        var response=RegistrationHttpResponses.toResponse(
                MutationResponse.success(ApiStatus.SUCCESS,"cancelled",List.of()));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void assertStatus(String code,ApiStatus apiStatus,int expected) {
        var error=new ApiError("BUSINESS",code,"message",List.of("PAGE"),"ERROR","SERVER");
        var response=RegistrationHttpResponses.toResponse(
                MutationResponse.failure(apiStatus,List.of(error)));
        assertThat(response.getStatusCode().value()).isEqualTo(expected);
    }
}
