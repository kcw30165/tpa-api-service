package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.RegistrationHttpResponses;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.dto.CaptchaRefreshRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.dto.CaptchaRefreshResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.dto.CaptchaChallengeResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.dto.CaptchaVerifyRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.dto.CaptchaVerifyResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.dto.CaptchaWorkflowResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.mapper.CaptchaWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CreateCaptchaChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.RefreshCaptchaChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.VerifyCaptchaChallengeCommand;
import com.bct.ngtpa.apiservice.application.port.in.CreateCaptchaChallengeUseCase;
import com.bct.ngtpa.apiservice.application.port.in.RefreshCaptchaUseCase;
import com.bct.ngtpa.apiservice.application.port.in.VerifyCaptchaUseCase;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth/captcha")
@RequiredArgsConstructor
public class AuthCaptchaController {

    private final CreateCaptchaChallengeUseCase createCaptchaChallengeUseCase;
    private final VerifyCaptchaUseCase verifyCaptchaUseCase;
    private final RefreshCaptchaUseCase refreshCaptchaUseCase;
    private final CaptchaWebMapper captchaWebMapper;

    @PostMapping(path = "/challenge", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<MutationResponse<CaptchaWorkflowResultResponse<CaptchaChallengeResponse>>> challenge(
            @RequestHeader(name = RequestCorrelation.REQUEST_ID_HEADER, required = false) String requestId,
            @RequestHeader(name = RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, required = false) String acceptLanguage,
            ServerHttpResponse response) {
        echoRequestId(response, requestId);
        return createCaptchaChallengeUseCase
                .create(new CreateCaptchaChallengeCommand(trimToNull(requestId), trimToNull(acceptLanguage)))
                .map(captchaWebMapper::toChallengeMutationResponse);
    }

    @PostMapping(path = "/verify", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<MutationResponse<CaptchaWorkflowResultResponse<CaptchaVerifyResponse>>>> verify(
            @RequestBody(required = false) CaptchaVerifyRequest request,
            @RequestHeader(name = RequestCorrelation.REQUEST_ID_HEADER, required = false) String requestId,
            @RequestHeader(name = RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, required = false) String acceptLanguage,
            ServerHttpResponse response) {
        echoRequestId(response, requestId);
        if (request == null || !hasText(request.captchaChallengeId())) {
            return Mono.just(RegistrationHttpResponses.toResponse(
                    captchaWebMapper.missingChallengeIdResponse(trimToNull(acceptLanguage))));
        }
        if (!hasText(request.answer())) {
            return Mono.just(RegistrationHttpResponses.toResponse(
                    captchaWebMapper.missingAnswerResponse(trimToNull(acceptLanguage))));
        }

        try {
            var command = new VerifyCaptchaChallengeCommand(
                    new CaptchaChallengeId(request.captchaChallengeId().trim()),
                    request.answer().trim(),
                    trimToNull(requestId));
            return verifyCaptchaUseCase.verify(command)
                    .map(result -> captchaWebMapper.toVerifyMutationResponse(result, trimToNull(acceptLanguage)))
                    .map(RegistrationHttpResponses::toResponse);
        } catch (RuntimeException exception) {
            return Mono.just(RegistrationHttpResponses.toResponse(
                    captchaWebMapper.invalidChallengeIdResponse(trimToNull(acceptLanguage))));
        }
    }

    @PostMapping(path = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<MutationResponse<CaptchaWorkflowResultResponse<CaptchaRefreshResponse>>>> refresh(
            @RequestBody(required = false) CaptchaRefreshRequest request,
            @RequestHeader(name = RequestCorrelation.REQUEST_ID_HEADER, required = false) String requestId,
            @RequestHeader(name = RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, required = false) String acceptLanguage,
            ServerHttpResponse response) {
        echoRequestId(response, requestId);

        try {
            CaptchaChallengeId oldCaptchaChallengeId = null;
            if (request != null && hasText(request.captchaChallengeId())) {
                oldCaptchaChallengeId = new CaptchaChallengeId(request.captchaChallengeId().trim());
            }
            return refreshCaptchaUseCase
                    .refresh(new RefreshCaptchaChallengeCommand(oldCaptchaChallengeId, trimToNull(requestId)))
                    .map(captchaWebMapper::toRefreshMutationResponse)
                    .map(RegistrationHttpResponses::toResponse);
        } catch (RuntimeException exception) {
            return Mono.just(RegistrationHttpResponses.toResponse(
                    captchaWebMapper.invalidRefreshChallengeIdResponse(trimToNull(acceptLanguage))));
        }
    }

    private void echoRequestId(ServerHttpResponse response, String requestId) {
        String trimmed = trimToNull(requestId);
        if (trimmed != null) {
            response.getHeaders().set(RequestCorrelation.REQUEST_ID_HEADER, trimmed);
        }
    }

    private boolean hasText(String value) {
        return trimToNull(value) != null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}