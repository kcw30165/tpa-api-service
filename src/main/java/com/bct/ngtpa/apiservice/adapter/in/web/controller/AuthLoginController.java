package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.LoginHttpResponses;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.dto.*;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.mapper.LoginStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.port.in.StartLoginUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.StartLoginStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthLoginController {
        private final StartLoginUseCase useCase;
        private final LoginStartWebMapper mapper;
        private final AuthFlowCookieWriter cookieWriter;

        @PostMapping("/login")
        public Mono<ResponseEntity<MutationResponse<LoginWorkflowResultResponse<LoginStartDataResponse>>>> startLogin(
                        @RequestBody(required = false) LoginStartRequest request,
                        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                        @RequestHeader(name = "X-Request-Id", required = false) String requestId,
                        @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage,
                        ServerHttpResponse response) {
                if (idempotencyKey == null || idempotencyKey.isBlank())
                        return Mono.just(LoginHttpResponses.toResponse(mapper.missingIdempotencyKey()));
                if (request == null)
                        return Mono.just(LoginHttpResponses.toResponse(mapper.invalidRequest()));
                try {
                        return useCase.start(mapper.toCommand(request, idempotencyKey, requestId, acceptLanguage))
                                        .map(result -> {
                                                if (result.status() == StartLoginStatus.STARTED || result
                                                                .status() == StartLoginStatus.COMPLETED_REPLAY) {
                                                        cookieWriter.writeAuthFlowCookie(response,
                                                                        result.authFlowSessionId());
                                                }
                                                return LoginHttpResponses.toResponse(
                                                                mapper.toMutationResponse(result, acceptLanguage));
                                        });
                } catch (RuntimeException exception) {
                        return Mono.just(LoginHttpResponses.toResponse(mapper.invalidRequest()));
                }
        }
}
