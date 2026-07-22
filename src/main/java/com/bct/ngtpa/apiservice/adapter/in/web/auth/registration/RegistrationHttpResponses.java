package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;

import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Registration-only translation from the generic envelope to precise HTTP semantics. */
public final class RegistrationHttpResponses {
    private RegistrationHttpResponses() {
    }

    public static <T> ResponseEntity<MutationResponse<T>> toResponse(MutationResponse<T> body) {
        HttpStatus status = status(body);
        var builder = ResponseEntity.status(status);
        Integer retryAfter = retryAfterSeconds(body);
        if (status == HttpStatus.TOO_MANY_REQUESTS && retryAfter != null && retryAfter > 0) {
            builder.header(HttpHeaders.RETRY_AFTER, Integer.toString(retryAfter));
        }
        return builder.body(body);
    }

    @SuppressWarnings("unchecked")
    public static ResponseEntity<Object> toObjectResponse(Object body) {
        if (body instanceof MutationResponse<?> mutation) {
            return (ResponseEntity<Object>) (ResponseEntity<?>) toResponse(mutation);
        }
        // Temporary compatibility for legacy mapper fallback objects. Patch 005
        // does not infer failure semantics from untyped maps.
        return ResponseEntity.ok(body);
    }

    private static HttpStatus status(MutationResponse<?> body) {
        if (body == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (body.success()) {
            return HttpStatus.OK;
        }
        String code = firstCode(body.errors());
        if (code.equals(RegistrationErrorKey.CSRF_INVALID.value())
                || code.equals(RegistrationErrorKey.ORIGIN_INVALID.value())) {
            return HttpStatus.FORBIDDEN;
        }
        if (code.equals(RegistrationErrorKey.STATE_INVALID.value())
                || code.startsWith(SharedWorkflowErrorKey.IDEMPOTENCY_IN_PROGRESS.value())
                || code.startsWith(SharedWorkflowErrorKey.IDEMPOTENCY_KEY_REUSED.value())) {
            return HttpStatus.CONFLICT;
        }
        if (code.equals(RegistrationErrorKey.OTP_SEND_RATE_LIMITED.value())
            || code.equals(RegistrationErrorKey.OTP_VERIFY_RATE_LIMITED.value()) || code.equals(RegistrationErrorKey.OTP_COOLDOWN_ACTIVE.value())
                || code.equals(RegistrationErrorKey.OTP_LOCKED.value())) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        if (body.status() == ApiStatus.DOWNSTREAM_ERROR) {
            return HttpStatus.BAD_GATEWAY;
        }
        if (body.status() == ApiStatus.SYSTEM_ERROR) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (body.status() == ApiStatus.DOWNSTREAM_REJECTED
                || body.status() == ApiStatus.BUSINESS_REJECTED) {
            return HttpStatus.UNPROCESSABLE_ENTITY;
        }
        return HttpStatus.BAD_REQUEST;
    }

    private static String firstCode(List<ApiError> errors) {
        if (errors == null || errors.isEmpty() || errors.get(0) == null
                || errors.get(0).code() == null) {
            return "";
        }
        return errors.get(0).code();
    }

    private static Integer retryAfterSeconds(MutationResponse<?> body) {
        Object result = body.result();
        if (result == null) {
            return null;
        }
        try {
            var method = result.getClass().getMethod("retryAfterSeconds");
            Object value = method.invoke(result);
            return value instanceof Integer number ? number : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
