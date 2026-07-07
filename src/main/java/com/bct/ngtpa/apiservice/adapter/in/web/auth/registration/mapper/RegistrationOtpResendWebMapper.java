package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationOtpResendResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.workflow.WorkflowMutationResponseMapper;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationWorkflowConstants;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationWorkflowPageRefs;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.RegistrationOtpResendErrorKeys;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.ResendRegistrationOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.ResendRegistrationOtpResultStatus;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResult;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResultStatus;

public class RegistrationOtpResendWebMapper {
    private static final String SUCCESS = "SUCCESS";
    private static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    private static final String BUSINESS_FAILED = "BUSINESS_FAILED";
    private static final String DOWNSTREAM_ERROR = "DOWNSTREAM_ERROR";
    private static final String SYSTEM_ERROR = "SYSTEM_ERROR";
    private static final String SOURCE_SERVER = "SERVER";

    private final WorkflowMutationResponseMapper workflowMutationResponseMapper;

    public RegistrationOtpResendWebMapper(WorkflowMutationResponseMapper workflowMutationResponseMapper) {
        this.workflowMutationResponseMapper = workflowMutationResponseMapper;
    }

    public Object toMutationResponse(ResendRegistrationOtpResult result) {
        if (workflowMutationResponseMapper != null) {
            return workflowMutationResponseMapper.toMutationResponse(toWorkflowResult(result));
        }
        return fallbackResponse(result);
    }


    private WorkflowResult<RegistrationOtpResendResponse> toWorkflowResult(ResendRegistrationOtpResult result) {
        return new WorkflowResult<>(
                workflowStatus(result.status()),
                result.nextStep(),
                data(result),
                RegistrationWorkflowPageRefs.forNextStep(result.nextStep()),
                result.messages(),
                result.errors());
    }

    private WorkflowResultStatus workflowStatus(ResendRegistrationOtpResultStatus status) {
        return switch (status) {
            case RESENT, COMPLETED_REPLAY -> WorkflowResultStatus.SUCCESS;
            case DUPLICATE_IN_PROGRESS -> WorkflowResultStatus.WAITING;
            case COOLDOWN_ACTIVE, MAX_RESEND_ATTEMPTS_REACHED -> WorkflowResultStatus.BUSINESS_FAILED;
            case MESSAGE_DELIVERY_FAILED, SYSTEM_FAILED -> WorkflowResultStatus.SYSTEM_FAILED;
            default -> WorkflowResultStatus.VALIDATION_FAILED;
        };
    }

    public Object missingAuthFlowCookieResponse() {
        return errorResponse(RegistrationOtpResendErrorKeys.REGISTRATION_AUTH_FLOW_INVALID, VALIDATION_FAILED);
    }

    public Object missingCsrfTokenResponse() {
        return errorResponse(RegistrationOtpResendErrorKeys.REGISTRATION_CSRF_INVALID, VALIDATION_FAILED);
    }

    public Object missingIdempotencyKeyResponse() {
        return errorResponse("workflow.idempotency.missing", VALIDATION_FAILED);
    }

    private Object tryMapWithWorkflowMapper(Object workflowResult) {
        for (Method method : workflowMutationResponseMapper.getClass().getMethods()) {
            if ((method.getName().equals("toMutationResponse") || method.getName().equals("map"))
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isAssignableFrom(workflowResult.getClass())) {
                try {
                    return method.invoke(workflowMutationResponseMapper, workflowResult);
                } catch (ReflectiveOperationException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private Object tryBuildWorkflowResult(ResendRegistrationOtpResult result) {
        try {
            Class<?> statusClass = Class.forName("com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResultStatus");
            Class<?> payloadClass = Class.forName("com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResultPayload");
            Class<?> resultClass = Class.forName("com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResult");
            Object payload = buildPayload(payloadClass, result);
            Object status = Enum.valueOf((Class<Enum>) statusClass.asSubclass(Enum.class), workflowStatusName(result.status()));
            for (Constructor<?> constructor : resultClass.getConstructors()) {
                Object[] args = argsFor(constructor.getParameterTypes(), statusClass, payloadClass, status, payload, result);
                if (args != null) {
                    return constructor.newInstance(args);
                }
            }
        } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
            return null;
        }
        return null;
    }

    private Object buildPayload(Class<?> payloadClass, ResendRegistrationOtpResult result) throws ReflectiveOperationException {
        RegistrationOtpResendResponse data = data(result);
        Object pageRef = RegistrationWorkflowPageRefs.forNextStep(result.nextStep());
        for (Constructor<?> constructor : payloadClass.getConstructors()) {
            Object[] args = argsForPayload(constructor.getParameterTypes(), result.nextStep(), data, pageRef);
            if (args != null) {
                return constructor.newInstance(args);
            }
        }
        return null;
    }

    private Object[] argsForPayload(Class<?>[] types, WorkflowNextStep nextStep, RegistrationOtpResendResponse data, Object pageRef) {
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            if (types[i].isAssignableFrom(WorkflowNextStep.class)) {
                args[i] = nextStep;
            } else if (types[i].isAssignableFrom(RegistrationOtpResendResponse.class) || types[i] == Object.class) {
                args[i] = data;
            } else if (pageRef != null && types[i].isAssignableFrom(pageRef.getClass())) {
                args[i] = pageRef;
            } else {
                args[i] = null;
            }
        }
        return args;
    }

    private Object[] argsFor(Class<?>[] types, Class<?> statusClass, Class<?> payloadClass, Object status, Object payload, ResendRegistrationOtpResult result) {
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            if (types[i].isAssignableFrom(statusClass)) {
                args[i] = status;
            } else if (payload != null && types[i].isAssignableFrom(payloadClass)) {
                args[i] = payload;
            } else if (types[i].isAssignableFrom(List.class)) {
                args[i] = typeName(types[i]).contains("Message") ? result.messages() : result.errors();
            } else if (types[i].isAssignableFrom(WorkflowNextStep.class)) {
                args[i] = result.nextStep();
            } else {
                args[i] = null;
            }
        }
        return args;
    }

    private String typeName(Class<?> type) {
        return type == null ? "" : type.getTypeName();
    }

    private String workflowStatusName(ResendRegistrationOtpResultStatus status) {
        return switch (status) {
            case RESENT, COMPLETED_REPLAY, DUPLICATE_IN_PROGRESS -> "SUCCESS";
            case COOLDOWN_ACTIVE, MAX_RESEND_ATTEMPTS_REACHED, IDEMPOTENCY_KEY_REUSED, INVALID_AUTH_FLOW, CSRF_INVALID, VALIDATION_FAILED -> "VALIDATION_FAILED";
            case MESSAGE_DELIVERY_FAILED -> "SYSTEM_FAILED";
            case SYSTEM_FAILED -> "SYSTEM_FAILED";
        };
    }

    private Map<String, Object> fallbackResponse(ResendRegistrationOtpResult result) {
        boolean success = result.status() == ResendRegistrationOtpResultStatus.RESENT
                || result.status() == ResendRegistrationOtpResultStatus.COMPLETED_REPLAY
                || result.status() == ResendRegistrationOtpResultStatus.DUPLICATE_IN_PROGRESS;
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", success);
        response.put("status", apiStatus(result.status()));
        response.put("result", success || exposeResendFailurePayload(result.status()) ? payloadMap(result) : null);
        response.put("messages", result.messages());
        response.put("errors", success ? List.of() : errorMaps(result));
        return response;
    }

    private Map<String, Object> payloadMap(ResendRegistrationOtpResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nextStep", result.nextStep());
        payload.put("data", data(result));
        Map<String, Object> pageRef = new LinkedHashMap<>();
        pageRef.put("pageId", RegistrationWorkflowConstants.PAGE_REGISTRATION_OTP_VERIFICATION);
        pageRef.put("variant", null);
        payload.put("pageRef", result.nextStep() == WorkflowNextStep.VERIFY_OTP ? pageRef : null);
        return payload;
    }

    private RegistrationOtpResendResponse data(ResendRegistrationOtpResult result) {
        if (result.status() != ResendRegistrationOtpResultStatus.RESENT
                && result.status() != ResendRegistrationOtpResultStatus.COMPLETED_REPLAY
                && !exposeResendFailurePayload(result.status())) {
            return null;
        }
        return new RegistrationOtpResendResponse(
                result.maskedDestination(),
                result.expiresInSeconds() > 0 ? result.expiresInSeconds() : null,
                result.resendCooldownSeconds() > 0 ? result.resendCooldownSeconds() : null,
                result.remainingResendAttempts(),
                result.retryAfterSeconds());
    }

    private boolean exposeResendFailurePayload(ResendRegistrationOtpResultStatus status) {
        return status == ResendRegistrationOtpResultStatus.COOLDOWN_ACTIVE
                || status == ResendRegistrationOtpResultStatus.MAX_RESEND_ATTEMPTS_REACHED;
    }

    private String apiStatus(ResendRegistrationOtpResultStatus status) {
        return switch (status) {
            case RESENT, COMPLETED_REPLAY, DUPLICATE_IN_PROGRESS -> SUCCESS;
            case MESSAGE_DELIVERY_FAILED -> DOWNSTREAM_ERROR;
            case SYSTEM_FAILED -> SYSTEM_ERROR;
            case COOLDOWN_ACTIVE, MAX_RESEND_ATTEMPTS_REACHED -> BUSINESS_FAILED;
            default -> VALIDATION_FAILED;
        };
    }

    private List<Map<String, Object>> errorMaps(ResendRegistrationOtpResult result) {
        if (!result.errors().isEmpty()) {
            return result.errors().stream().map(error -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("code", error.code());
                map.put("target", error.targets() == null || error.targets().isEmpty() ? "PAGE" : error.targets().get(0));
                return map;
            }).toList();
        }
        return List.of(errorMap(defaultErrorCode(result.status())));
    }

    private Map<String, Object> errorMap(String code) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", code);
        map.put("target", "PAGE");
        return map;
    }

    private Object errorResponse(String code, String status) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("status", status);
        response.put("result", null);
        response.put("messages", List.of());
        response.put("errors", List.of(errorMap(code)));
        return response;
    }

    private String defaultErrorCode(ResendRegistrationOtpResultStatus status) {
        return switch (status) {
            case INVALID_AUTH_FLOW -> RegistrationOtpResendErrorKeys.REGISTRATION_AUTH_FLOW_INVALID;
            case CSRF_INVALID -> RegistrationOtpResendErrorKeys.REGISTRATION_CSRF_INVALID;
            case IDEMPOTENCY_KEY_REUSED -> RegistrationOtpResendErrorKeys.WORKFLOW_IDEMPOTENCY_KEY_REUSED;
            case COOLDOWN_ACTIVE -> RegistrationOtpResendErrorKeys.REGISTRATION_OTP_COOLDOWN_ACTIVE;
            case MAX_RESEND_ATTEMPTS_REACHED -> RegistrationOtpResendErrorKeys.REGISTRATION_OTP_MAX_ATTEMPTS_REACHED;
            case MESSAGE_DELIVERY_FAILED -> RegistrationOtpResendErrorKeys.REGISTRATION_MESSAGE_DELIVERY_FAILED;
            default -> RegistrationOtpResendErrorKeys.REGISTRATION_OTP_RESEND_FAILED;
        };
    }
}
