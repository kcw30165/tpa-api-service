package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.ForgotPasswordHttpResponses;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordCancelDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordCompleteDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordCompleteRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordDetailsRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordIdentityCheckRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordStartDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordStartRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordWorkflowResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordCancelWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordCompleteWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordDetailsOtpWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordIdentityWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.XsrfCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.port.in.CancelForgotPasswordUseCase;
import com.bct.ngtpa.apiservice.application.port.in.CompleteForgotPasswordUseCase;
import com.bct.ngtpa.apiservice.application.port.in.ProcessForgotPasswordDetailsOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.in.StartForgotPasswordUseCase;
import com.bct.ngtpa.apiservice.application.port.in.VerifyForgotPasswordIdentityUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp.ForgotPasswordDetailsOtpCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp.ForgotPasswordDetailsOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.identity.VerifyForgotPasswordIdentityResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.identity.VerifyForgotPasswordIdentityStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.StartForgotPasswordResultStatus;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/forgot-password")
public class AuthForgotPasswordController {
    private final StartForgotPasswordUseCase startUseCase;
    private final ForgotPasswordStartWebMapper startMapper;
    private final VerifyForgotPasswordIdentityUseCase identityCheckUseCase;
    private final ForgotPasswordIdentityWebMapper identityCheckMapper;
    private final ProcessForgotPasswordDetailsOtpUseCase detailsCheckUseCase;
    private final ForgotPasswordDetailsOtpWebMapper detailsCheckMapper;
    private final CompleteForgotPasswordUseCase completeUseCase;
    private final ForgotPasswordCompleteWebMapper completeMapper;
    private final CancelForgotPasswordUseCase cancelUseCase;
    private final ForgotPasswordCancelWebMapper cancelMapper;
    private final AuthFlowCookieWriter cookieWriter;
    private final XsrfCookieWriter xsrfCookies;

    @PostMapping("/start")
    public Mono<ResponseEntity<MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordStartDataResponse>>>> startForgotPassword(
            @RequestBody(required = false) ForgotPasswordStartRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(name = RequestCorrelation.REQUEST_ID_HEADER, required = false) String requestId,
            @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage,
            @CookieValue(name = "auth_flow_sid", required = false) String authFlowSessionId,
            ServerHttpResponse response) {
        echoRequestId(response, requestId);
        if (!hasText(idempotencyKey)) {
            return Mono.just(ForgotPasswordHttpResponses.toResponse(startMapper.missingIdempotencyKey()));
        }
        if (request == null) {
            return Mono.just(ForgotPasswordHttpResponses.toResponse(startMapper.invalidRequest()));
        }
        try {
            return startUseCase.start(startMapper.toCommand(
                    request,
                    idempotencyKey.trim(),
                    requestId,
                    acceptLanguage,
                    authFlowSessionId))
                    .map(result -> {
                        if (result.status() == StartForgotPasswordResultStatus.STARTED
                                || result.status() == StartForgotPasswordResultStatus.COMPLETED_REPLAY) {
                            cookieWriter.writeAuthFlowCookie(response, result.authFlowSessionId());
                        } else if (result.status() == StartForgotPasswordResultStatus.AUTH_FLOW_INVALID) {
                            cookieWriter.clearAuthFlowCookie(response);
                            xsrfCookies.clearXsrfCookie(response);
                        }
                        return ForgotPasswordHttpResponses.toResponse(
                                startMapper.toMutationResponse(result, acceptLanguage));
                    })
                    .onErrorResume(exception -> Mono.just(
                            ForgotPasswordHttpResponses.toResponse(startMapper.systemUnavailable())));
        } catch (RuntimeException exception) {
            return Mono.just(ForgotPasswordHttpResponses.toResponse(startMapper.invalidRequest()));
        }
    }

    @PostMapping("/identity-check")
    public Mono<ResponseEntity<?>> check(@RequestBody(required = false) ForgotPasswordIdentityCheckRequest body,
            @CookieValue(name = "auth_flow_sid", required = false) String sid,
            @RequestHeader(name = "X-XSRF-TOKEN", required = false) String csrf,
            @RequestHeader(name = "Origin", required = false) String origin,
            @RequestHeader(name = "Referer", required = false) String referer,
            @RequestHeader(name = RequestCorrelation.REQUEST_ID_HEADER, required = false) String requestId,
            @RequestHeader(name = "Accept-Language", required = false) String language,
            ServerHttpResponse response) {
        echoRequestId(response, requestId);
        if (!hasText(sid) || !hasText(csrf))
            return Mono.just(ForgotPasswordHttpResponses.toResponse(identityCheckMapper.response(
                    VerifyForgotPasswordIdentityResult.of(VerifyForgotPasswordIdentityStatus.SYSTEM_FAILED))));
        try {
            return identityCheckUseCase.verify(identityCheckMapper.command(body, sid, csrf, origin, referer, requestId))
                    .map(r -> ForgotPasswordHttpResponses.toResponse(identityCheckMapper.response(r)));
        } catch (RuntimeException e) {
            return Mono.just(ForgotPasswordHttpResponses.toResponse(identityCheckMapper.response(
                    VerifyForgotPasswordIdentityResult.of(VerifyForgotPasswordIdentityStatus.SYSTEM_FAILED))));
        }
    }

    @PostMapping({ "/details-check", "/details" })
    public Mono<ResponseEntity<?>> details(@RequestBody(required = false) ForgotPasswordDetailsRequest b,
            @CookieValue(name = "auth_flow_sid", required = false) String sid,
            @RequestHeader(name = "X-XSRF-TOKEN", required = false) String csrf,
            @RequestHeader(name = "Origin", required = false) String origin,
            @RequestHeader(name = "Referer", required = false) String referer,
            @RequestHeader(name = RequestCorrelation.REQUEST_ID_HEADER, required = false) String requestId,
            @RequestHeader(name = "Accept-Language", required = false) String lang,
            @RequestHeader(name = "Idempotency-Key", required = false) String key,
            ServerHttpResponse response) {
        echoRequestId(response, requestId);
        if (b == null || !hasText(sid) || !hasText(csrf) || !hasText(key))
            return Mono
                    .just(ForgotPasswordHttpResponses
                            .toResponse(detailsCheckMapper.map(ForgotPasswordDetailsOtpResult.system())));
        try {
            return detailsCheckUseCase
                    .process(new ForgotPasswordDetailsOtpCommand(new AuthFlowSessionId(sid), csrf, origin, referer,
                            requestId, lang, key, b.dateOfBirth(), b.email(), b.mobile()))
                    .map(r -> ForgotPasswordHttpResponses.toResponse(detailsCheckMapper.map(r)));
        } catch (RuntimeException e) {
            return Mono
                    .just(ForgotPasswordHttpResponses
                            .toResponse(detailsCheckMapper.map(ForgotPasswordDetailsOtpResult.system())));
        }
    }

    @PostMapping(path = "/cancel", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordCancelDataResponse>>>> cancel(
            @CookieValue(name = "auth_flow_sid", required = false) String sessionId,
            @RequestHeader(name = "X-XSRF-TOKEN", required = false) String csrfToken,
            @RequestHeader(name = "Origin", required = false) String origin,
            @RequestHeader(name = "Referer", required = false) String referer,
            @RequestHeader(name = RequestCorrelation.REQUEST_ID_HEADER, required = false) String requestId,
            @RequestHeader(name = "Accept-Language", required = false) String language,
            ServerHttpResponse response) {
        echoRequestId(response, requestId);
        if (!hasText(sessionId)) {
            cookieWriter.clearAuthFlowCookie(response);
            xsrfCookies.clearXsrfCookie(response);
            return Mono.just(ForgotPasswordHttpResponses.toResponse(cancelMapper.missingSession()));
        }
        if (!hasText(csrfToken)) {
            return Mono.just(ForgotPasswordHttpResponses.toResponse(cancelMapper.missingCsrf()));
        }
        try {
            return cancelUseCase.cancel(cancelMapper.toCommand(
                    sessionId,
                    csrfToken,
                    origin,
                    referer,
                    requestId))
                    .map(result -> {
                        if (result.clearAuthFlowCookie()) {
                            cookieWriter.clearAuthFlowCookie(response);
                        }
                        if (result.clearXsrfCookie()) {
                            xsrfCookies.clearXsrfCookie(response);
                        }
                        return ForgotPasswordHttpResponses.toResponse(cancelMapper.toResponse(result));
                    })
                    .onErrorResume(error -> Mono
                            .just(ForgotPasswordHttpResponses.toResponse(cancelMapper.systemUnavailable())));
        } catch (RuntimeException exception) {
            return Mono.just(ForgotPasswordHttpResponses.toResponse(cancelMapper.csrfInvalid()));
        }
    }

    @PostMapping(path = "/complete", consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE, produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordCompleteDataResponse>>>> complete(
            @RequestBody(required = false) ForgotPasswordCompleteRequest request,
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
            return Mono.just(ForgotPasswordHttpResponses.toResponse(completeMapper.missingAuthFlowCookie()));
        }
        if (!hasText(csrfToken)) {
            return Mono.just(ForgotPasswordHttpResponses.toResponse(completeMapper.missingCsrfToken()));
        }
        if (!hasText(idempotencyKey)) {
            return Mono.just(ForgotPasswordHttpResponses.toResponse(completeMapper.missingIdempotencyKey()));
        }
        if (request == null) {
            return Mono.just(ForgotPasswordHttpResponses.toResponse(completeMapper.invalidRequest()));
        }
        try {
            return completeUseCase.complete(completeMapper.toCommand(
                    request,
                    sessionId,
                    csrfToken,
                    idempotencyKey,
                    origin,
                    referer,
                    language,
                    requestId))
                    .map(result -> {
                        if (result.clearAuthFlowCookie()) {
                            cookieWriter.clearAuthFlowCookie(response);
                        }
                        if (result.clearXsrfCookie()) {
                            xsrfCookies.clearXsrfCookie(response);
                        }
                        return ForgotPasswordHttpResponses.toResponse(completeMapper.toResponse(result));
                    })
                    .onErrorResume(error -> Mono
                            .just(ForgotPasswordHttpResponses.toResponse(completeMapper.systemUnavailable())));
        } catch (RuntimeException exception) {
            return Mono.just(ForgotPasswordHttpResponses.toResponse(completeMapper.invalidRequest()));
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
}