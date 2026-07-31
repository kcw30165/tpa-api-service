package com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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
        if ("err.workflow.idempotency.inProgress".equals(code)
                || "err.workflow.idempotency.keyReused".equals(code)) {
            return HttpStatus.CONFLICT;
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
        return errors == null || errors.isEmpty() || errors.get(0) == null || errors.get(0).code() == null
                ? ""
                : errors.get(0).code();
    }
}