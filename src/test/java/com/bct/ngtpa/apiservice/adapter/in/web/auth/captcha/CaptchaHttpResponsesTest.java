package com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha;

import static org.assertj.core.api.Assertions.assertThat;
import com.bct.ngtpa.apiservice.adapter.in.web.response.*;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

class CaptchaHttpResponsesTest {
    private final CaptchaHttpResponses responses = new CaptchaHttpResponses();

    @Test
    void successRemainsOkWithoutRegistrationHeaders() {
        var entity = responses.toResponse(MutationResponse.success(ApiStatus.SUCCESS, "ok", List.of()));
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entity.getHeaders().get(HttpHeaders.SET_COOKIE)).isNullOrEmpty();
    }

    @Test
    void captchaVerificationInvalidIsUnprocessable() {
        var body = MutationResponse.failure(ApiStatus.VALIDATION_FAILED, List
                .of(ApiError.field(ErrorCodes.CAPTCHA_VERIFICATION_INVALID, null, List.of("captchaAnswer"), "SERVER")));
        assertThat(responses.toResponse(body).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void unrelatedValidationRemainsBadRequest() {
        var body = MutationResponse.failure(ApiStatus.VALIDATION_FAILED,
                List.of(ApiError.field("err.request.validation.failed", null, List.of("field"), "SERVER")));
        assertThat(responses.toResponse(body).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void systemErrorRemainsServiceUnavailable() {
        assertThat(responses.toResponse(MutationResponse.failure(ApiStatus.SYSTEM_ERROR, List.of())).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
