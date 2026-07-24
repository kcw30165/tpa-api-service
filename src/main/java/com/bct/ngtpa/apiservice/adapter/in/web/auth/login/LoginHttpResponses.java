package com.bct.ngtpa.apiservice.adapter.in.web.auth.login;

import com.bct.ngtpa.apiservice.adapter.in.web.response.*;
import com.bct.ngtpa.apiservice.application.dto.workflow.*;
import java.util.List;
import org.springframework.http.*;

public final class LoginHttpResponses {
    private LoginHttpResponses() {
    }

    public static <T> ResponseEntity<MutationResponse<T>> toResponse(MutationResponse<T> body) {
        return ResponseEntity.status(status(body)).body(body);
    }

    private static HttpStatus status(MutationResponse<?> body) {
        if (body == null)
            return HttpStatus.INTERNAL_SERVER_ERROR;
        if (body.success())
            return HttpStatus.OK;
        String code = firstCode(body.errors());
        if (code.equals(SharedWorkflowErrorKey.IDEMPOTENCY_IN_PROGRESS.value())
                || code.equals(SharedWorkflowErrorKey.IDEMPOTENCY_KEY_REUSED.value()))
            return HttpStatus.CONFLICT;
        if (code.equals(LoginErrorKey.OTP_VERIFY_ATTEMPTS_EXCEEDED.value())
                || code.equals(LoginErrorKey.OTP_COOLDOWN_ACTIVE.value()))
            return HttpStatus.TOO_MANY_REQUESTS;
        if (code.equals(LoginErrorKey.CREDENTIALS_UNAVAILABLE.value()))
            return HttpStatus.BAD_GATEWAY;
        if (code.equals(LoginErrorKey.SESSION_CREATION_FAILED.value()))
            return HttpStatus.SERVICE_UNAVAILABLE;
        if (body.status() == ApiStatus.BUSINESS_REJECTED || body.status() == ApiStatus.DOWNSTREAM_REJECTED)
            return HttpStatus.UNPROCESSABLE_ENTITY;
        if (body.status() == ApiStatus.DOWNSTREAM_ERROR)
            return HttpStatus.BAD_GATEWAY;
        if (body.status() == ApiStatus.SYSTEM_ERROR)
            return HttpStatus.SERVICE_UNAVAILABLE;
        return HttpStatus.BAD_REQUEST;
    }

    private static String firstCode(List<ApiError> errors) {
        return errors == null || errors.isEmpty() || errors.get(0) == null || errors.get(0).code() == null ? ""
                : errors.get(0).code();
    }
}
