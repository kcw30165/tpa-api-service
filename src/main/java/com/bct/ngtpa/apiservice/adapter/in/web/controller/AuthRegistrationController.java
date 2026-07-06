package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationStartRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationStartWorkflowDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResultPayload;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationStartWebMapper;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationResultStatus;

import lombok.RequiredArgsConstructor;

import com.bct.ngtpa.apiservice.application.port.in.StartRegistrationUseCase;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth/registration")
@RequiredArgsConstructor
public class AuthRegistrationController {
    private final StartRegistrationUseCase useCase;
    private final RegistrationStartWebMapper mapper;
    private final AuthFlowCookieWriter cookieWriter;

    @PostMapping("/start")
    public Mono<MutationResponse<WorkflowResultPayload<RegistrationStartWorkflowDataResponse>>> startRegistration(
            @RequestBody RegistrationStartRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(name = "X-Request-Id", required = false) String requestId,
            @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage,
            @CookieValue(name = "auth_flow_sid", required = false) String authFlowSessionIdCookie,
            ServerHttpResponse response) {
        if (idempotencyKey == null || idempotencyKey.isBlank())
            return Mono.just(mapper.missingIdempotencyKeyResponse(acceptLanguage));
        return useCase
                .start(mapper.toCommand(request, idempotencyKey, requestId, acceptLanguage, authFlowSessionIdCookie))
                .map(result -> {
                    if (result.status() == StartRegistrationResultStatus.STARTED)
                        cookieWriter.writeAuthFlowCookie(response, result.authFlowSessionId());
                    return mapper.toMutationResponse(result, acceptLanguage);
                });
    }
}
