package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationUsernameCheckRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationUsernameCheckWebMapper;
import com.bct.ngtpa.apiservice.application.port.in.CheckRegistrationUsernameUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.CheckRegistrationUsernameCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth/registration")
@RequiredArgsConstructor
public class AuthRegistrationUsernameController {
    private final CheckRegistrationUsernameUseCase checkRegistrationUsernameUseCase;
    private final RegistrationUsernameCheckWebMapper registrationUsernameCheckWebMapper;

    @PostMapping(path = "/username/check", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Object> checkUsername(
            @RequestBody(required = false) RegistrationUsernameCheckRequest request,
            @CookieValue(name = "auth_flow_sid", required = false) String authFlowSessionId,
            @RequestHeader(name = "X-XSRF-TOKEN", required = false) String csrfToken,
            @RequestHeader(name = "X-Request-Id", required = false) String requestId,
            @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage,
            @RequestHeader(name = "Origin", required = false) String origin,
            @RequestHeader(name = "Referer", required = false) String referer) {
        if (!hasText(authFlowSessionId)) {
            return Mono.just(registrationUsernameCheckWebMapper.missingAuthFlowCookieResponse());
        }
        if (!hasText(csrfToken)) {
            return Mono.just(registrationUsernameCheckWebMapper.missingCsrfTokenResponse());
        }
        if (request == null) {
            return Mono.just(registrationUsernameCheckWebMapper.missingRequestBodyResponse());
        }

        CheckRegistrationUsernameCommand command;
        try {
            command = registrationUsernameCheckWebMapper.toCommand(
                    request,
                    authFlowSessionId,
                    csrfToken,
                    requestId,
                    acceptLanguage,
                    origin,
                    referer);
        } catch (RuntimeException exception) {
            return Mono.just(registrationUsernameCheckWebMapper.missingAuthFlowCookieResponse());
        }

        return checkRegistrationUsernameUseCase.check(command)
                .map(registrationUsernameCheckWebMapper::toMutationResponse);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
