package com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.workflow.ForgotPasswordErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Slf4j
public final class ForgotPasswordHttpResponses {
    private ForgotPasswordHttpResponses() {
    }

    public static <T> ResponseEntity<MutationResponse<T>> toResponse(MutationResponse<T> body) {
        return ResponseEntity.status(status(body)).body(body);
    }

    private static HttpStatus status(MutationResponse<?> body) {
        if (body == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (body.success()) {
            return HttpStatus.OK;
        }
        String code = firstCode(body.errors());
        log.debug("Mapping error code {} to HTTP status", code);
        if (SharedWorkflowErrorKey.IDEMPOTENCY_IN_PROGRESS.value().equals(code)
                || SharedWorkflowErrorKey.IDEMPOTENCY_KEY_REUSED.value().equals(code)) {
            return HttpStatus.CONFLICT;
        }
        if (ForgotPasswordErrorKey.SECURITY_INVALID.value().equals(code)
                || ForgotPasswordErrorKey.CSRF_INVALID.value().equals(code)) {
            return HttpStatus.FORBIDDEN;
        }
        if (ForgotPasswordErrorKey.OTP_MAX_ATTEMPTS_REACHED.value().equals(code)) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        if (body.status() == ApiStatus.BUSINESS_REJECTED) {
            return HttpStatus.UNPROCESSABLE_ENTITY;
        }
        if (body.status() == ApiStatus.SYSTEM_ERROR) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.BAD_REQUEST;
    }

    private static String firstCode(List<ApiError> errors) {
        return errors == null
                || errors.isEmpty()
                || errors.getFirst() == null
                || errors.getFirst().code() == null
                        ? ""
                        : errors.getFirst().code();
    }
}