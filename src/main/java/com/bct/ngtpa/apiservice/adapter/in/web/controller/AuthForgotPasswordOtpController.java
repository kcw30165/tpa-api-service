package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.ForgotPasswordHttpResponses;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordOtpDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordOtpVerifyRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordPasswordSetupDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordWorkflowResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordOtpResendWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordOtpVerifyWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.port.in.ResendForgotPasswordOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.in.VerifyForgotPasswordOtpUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ResendForgotPasswordOtpCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify.VerifyForgotPasswordOtpCommand;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth/forgot-password/otp")
public final class AuthForgotPasswordOtpController {
    private final ResendForgotPasswordOtpUseCase useCase;
    private final ForgotPasswordOtpResendWebMapper mapper;
    private final VerifyForgotPasswordOtpUseCase verifyUseCase;
    private final ForgotPasswordOtpVerifyWebMapper verifyMapper;

    public AuthForgotPasswordOtpController(ResendForgotPasswordOtpUseCase useCase,
            ForgotPasswordOtpResendWebMapper mapper) {
        this(useCase, mapper, null, null);
    }

    @Autowired
    public AuthForgotPasswordOtpController(ResendForgotPasswordOtpUseCase useCase,
            ForgotPasswordOtpResendWebMapper mapper,
            VerifyForgotPasswordOtpUseCase verifyUseCase,
            ForgotPasswordOtpVerifyWebMapper verifyMapper) {
        this.useCase = useCase;
        this.mapper = mapper;
        this.verifyUseCase = verifyUseCase;
        this.verifyMapper = verifyMapper;
    }

    @PostMapping("/resend")
    public Mono<ResponseEntity<MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordOtpDataResponse>>>> resend(
            @CookieValue(name = "auth_flow_sid", required = false) String sessionId,
            @RequestHeader(name = "X-XSRF-TOKEN", required = false) String csrfToken,
            @RequestHeader(name = "Origin", required = false) String origin,
            @RequestHeader(name = "Referer", required = false) String referer,
            @RequestHeader(name = RequestCorrelation.REQUEST_ID_HEADER, required = false) String requestId,
            @RequestHeader(name = "Accept-Language", required = false) String language,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            ServerHttpResponse response) {
        echoRequestId(response, requestId);
        if (!hasText(idempotencyKey)) {
            return Mono.just(ForgotPasswordHttpResponses.toResponse(mapper.missingIdempotencyKey()));
        }
        if (!hasText(sessionId) || !hasText(csrfToken)) {
            return Mono.just(ForgotPasswordHttpResponses.toResponse(mapper.systemUnavailable()));
        }
        try {
            return useCase.resend(new ResendForgotPasswordOtpCommand(
                    new AuthFlowSessionId(sessionId.trim()),
                    csrfToken.trim(),
                    clean(origin),
                    clean(referer),
                    clean(requestId),
                    clean(language),
                    idempotencyKey.trim()))
                    .map(mapper::toMutationResponse)
                    .map(ForgotPasswordHttpResponses::toResponse)
                    .onErrorResume(exception -> Mono.just(ForgotPasswordHttpResponses.toResponse(
                            mapper.systemUnavailable())));
        } catch (RuntimeException exception) {
            return Mono.just(ForgotPasswordHttpResponses.toResponse(mapper.systemUnavailable()));
        }
    }

    @PostMapping(path = "/verify")
    public Mono<ResponseEntity<MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordPasswordSetupDataResponse>>>> verify(
            @RequestBody(required = false) ForgotPasswordOtpVerifyRequest request,
            @CookieValue(name = "auth_flow_sid", required = false) String sessionId,
            @RequestHeader(name = "X-XSRF-TOKEN", required = false) String csrfToken,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(name = RequestCorrelation.REQUEST_ID_HEADER, required = false) String requestId,
            @RequestHeader(name = "Accept-Language", required = false) String language,
            @RequestHeader(name = "Origin", required = false) String origin,
            @RequestHeader(name = "Referer", required = false) String referer,
            ServerHttpResponse response) {
        echoRequestId(response, requestId);
        if (!hasText(sessionId)) {
            return Mono.just(ForgotPasswordHttpResponses.toResponse(verifyMapper.missingAuthFlowCookie()));
        }
        if (!hasText(csrfToken)) {
            return Mono.just(ForgotPasswordHttpResponses.toResponse(verifyMapper.missingCsrfToken()));
        }
        if (!hasText(idempotencyKey)) {
            return Mono.just(ForgotPasswordHttpResponses.toResponse(verifyMapper.missingIdempotencyKey()));
        }
        if (request == null || !hasText(request.code())) {
            return Mono.just(ForgotPasswordHttpResponses.toResponse(verifyMapper.missingCode()));
        }
        try {
            return verifyUseCase.verify(new VerifyForgotPasswordOtpCommand(
                    new AuthFlowSessionId(sessionId.trim()),
                    csrfToken.trim(),
                    request.code().trim(),
                    clean(origin),
                    clean(referer),
                    idempotencyKey.trim(),
                    clean(language),
                    clean(requestId),
                    Duration.ofSeconds(300)))
                    .map(verifyMapper::toMutationResponse)
                    .map(ForgotPasswordHttpResponses::toResponse)
                    .onErrorResume(exception -> Mono.just(ForgotPasswordHttpResponses.toResponse(
                            verifyMapper.systemUnavailable())));
        } catch (RuntimeException exception) {
            return Mono.just(ForgotPasswordHttpResponses.toResponse(verifyMapper.systemUnavailable()));
        }
    }

    private void echoRequestId(ServerHttpResponse response, String requestId) {
        if (hasText(requestId)) {
            response.getHeaders().set(RequestCorrelation.REQUEST_ID_HEADER, requestId.trim());
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String clean(String value) {
        return hasText(value) ? value.trim() : null;
    }
}