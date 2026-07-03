package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.*;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationStartWebMapper;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationResultStatus;
import com.bct.ngtpa.apiservice.application.port.in.StartRegistrationUseCase;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth/registration")
public class AuthRegistrationController {
    private final StartRegistrationUseCase useCase;
    private final RegistrationStartWebMapper mapper;
    private final AuthFlowCookieWriter cookieWriter;
    public AuthRegistrationController(StartRegistrationUseCase useCase, RegistrationStartWebMapper mapper, AuthFlowCookieWriter cookieWriter) { this.useCase = useCase; this.mapper = mapper; this.cookieWriter = cookieWriter; }
    @PostMapping("/start")
    public Mono<SimpleMutationResponse<RegistrationStartResponse>> startRegistration(@RequestBody RegistrationStartRequest request, @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey, @RequestHeader(name = "X-Request-Id", required = false) String requestId, @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage, ServerHttpResponse response) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return Mono.just(mapper.missingIdempotencyKeyResponse());
        return useCase.start(mapper.toCommand(request, idempotencyKey, requestId, acceptLanguage)).map(result -> { if (result.status() == StartRegistrationResultStatus.STARTED) cookieWriter.writeAuthFlowCookie(response, result.authFlowSessionId()); return mapper.toMutationResponse(result); });
    }
}
