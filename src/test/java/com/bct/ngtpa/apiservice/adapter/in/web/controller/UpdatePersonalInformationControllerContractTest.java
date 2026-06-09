package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.mockito.Mockito.mock;

import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestHeaderContextWebFilter;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingWebFilter;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateResponseMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.request.UpdatePersonalInformationRequest;
import com.bct.ngtpa.apiservice.application.port.in.UpdatePersonalInformationUseCase;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizerProperties;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class UpdatePersonalInformationControllerContractTest {

    private static final String REQUEST_ID = "client-request-uuid";
    private static final String ACCEPT_LANGUAGE = "en";

    @Test
    void updatePersonalInformationReturnsGenericBaseErrorWhenAccountRefMissing() {
        client()
                .put()
                .uri("/api/v1/personal-information")
                .header(RequestCorrelation.REQUEST_ID_HEADER, REQUEST_ID)
                .header(RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, ACCEPT_LANGUAGE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UpdatePersonalInformationRequest(
                        "1.0",
                        true,
                        Map.of("emailAddress", "nick@example.test")))
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().valueEquals(RequestCorrelation.REQUEST_ID_HEADER, REQUEST_ID)
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.messages").isArray()
                .jsonPath("$.errors").isArray()
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.MEMBER_CONTEXT_INVALID)
                .jsonPath("$.errorCode").doesNotExist()
                .jsonPath("$.requestId").doesNotExist();
    }

    private WebTestClient client() {
        UpdatePersonalInformationUseCase useCase = command -> Mono.empty();
        PersonalInformationUpdateWebMapper requestMapper = mock(PersonalInformationUpdateWebMapper.class);
        PersonalInformationUpdateResponseMapper responseMapper = mock(PersonalInformationUpdateResponseMapper.class);

        return WebTestClient.bindToController(new UpdatePersonalInformationController(
                        useCase,
                        requestMapper,
                        responseMapper))
                .webFilter(requestHeaderContextWebFilter())
                .controllerAdvice(new ApiExceptionHandler(testErrorMessageResolver(), testLoggingSanitizer()))
                .build();
    }

    private RequestHeaderContextWebFilter requestHeaderContextWebFilter() {
        return new RequestHeaderContextWebFilter(
                new RequestLoggingWebFilter(new RequestLoggingProperties(), testLoggingSanitizer(), new ObjectMapper()));
    }

    private static ErrorMessageResolver testErrorMessageResolver() {
        return (errorCode, locale, accountEnv, trustCode, schemeType) -> {
            if (ErrorCodes.MEMBER_CONTEXT_INVALID.equals(errorCode)) {
                return "Member context is invalid.";
            }
            return errorCode;
        };
    }

    private static LoggingSanitizer testLoggingSanitizer() {
        LoggingSanitizerProperties properties = new LoggingSanitizerProperties();
        properties.setSensitiveTokens(List.of("policyNo", "certNo", "userId", "apiKey", "token"));
        return new LoggingSanitizer(new ObjectMapper(), properties);
    }
}
