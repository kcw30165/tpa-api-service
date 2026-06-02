package com.bct.ngtpa.apiservice.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingWebFilter;
import com.bct.ngtpa.apiservice.application.dto.GetReferenceDataCountriesCommand;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDataCountriesResult;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDataOption;
import com.bct.ngtpa.apiservice.application.port.in.GetReferenceDataCountriesUseCase;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizerProperties;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class ReferenceDataCountriesControllerContractTest {

    private static final String ACCOUNT_REF = "ACC-123";
    private static final String REQUEST_ID = "client-request-uuid";
    private static final String ACCEPT_LANGUAGE = "en";

    private GetReferenceDataCountriesUseCase useCase;
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        useCase = Mockito.mock(GetReferenceDataCountriesUseCase.class);

        ReferenceDataController controller = new ReferenceDataController(useCase);
        ApiExceptionHandler exceptionHandler = new ApiExceptionHandler(
                new ErrorMessageResolver() {
                    @Override
                    public String resolve(String errorCode, String locale, String accountEnv, String trustCode,
                            String schemeType) {
                        if (ErrorCodes.MEMBER_CONTEXT_INVALID.equals(errorCode)) {
                            return "Member context is invalid.";
                        }
                        return "Unexpected error.";
                    }
                },
                new LoggingSanitizer(new ObjectMapper(), new LoggingSanitizerProperties()));

        client = WebTestClient.bindToController(controller)
                .controllerAdvice(exceptionHandler)
                .webFilter(new RequestLoggingWebFilter(
                        requestLoggingProperties(),
                        new LoggingSanitizer(new ObjectMapper(), new LoggingSanitizerProperties()),
                        new ObjectMapper()))
                .build();
    }

    @Test
    void getCountriesReturns200WithCountriesAndCallingCodesArrays() {
        when(useCase.execute(any())).thenReturn(Mono.just(new ReferenceDataCountriesResult(
                List.of(
                        new ReferenceDataOption("HKG", "Hong Kong"),
                        new ReferenceDataOption("CHN", "China")),
                List.of(
                        new ReferenceDataOption("852", "Hong Kong"),
                        new ReferenceDataOption("86", "China")))));

        client.get()
                .uri("/api/v1/reference-data/countries")
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, ACCOUNT_REF)
                .header(RequestCorrelation.REQUEST_ID_HEADER, REQUEST_ID)
                .header(RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, ACCEPT_LANGUAGE)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(RequestCorrelation.REQUEST_ID_HEADER, REQUEST_ID)
                .expectBody()
                .jsonPath("$.countries[0].value").isEqualTo("HKG")
                .jsonPath("$.countries[0].text").isEqualTo("Hong Kong")
                .jsonPath("$.countries[1].value").isEqualTo("CHN")
                .jsonPath("$.countries[1].text").isEqualTo("China")
                .jsonPath("$.callingCodes[0].value").isEqualTo("852")
                .jsonPath("$.callingCodes[0].text").isEqualTo("Hong Kong")
                .jsonPath("$.callingCodes[1].value").isEqualTo("86")
                .jsonPath("$.callingCodes[1].text").isEqualTo("China");
    }

    @Test
    void passesAccountRefAndAcceptLanguageIntoUseCaseCommand() {
        AtomicReference<GetReferenceDataCountriesCommand> captured = new AtomicReference<>();

        when(useCase.execute(any())).thenAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return Mono.just(new ReferenceDataCountriesResult(List.of(), List.of()));
        });

        client.get()
                .uri("/api/v1/reference-data/countries")
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, ACCOUNT_REF)
                .header(RequestCorrelation.REQUEST_ID_HEADER, REQUEST_ID)
                .header(RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, ACCEPT_LANGUAGE)
                .exchange()
                .expectStatus().isOk();

        assertEquals(ACCOUNT_REF, captured.get().accountRef());
        assertEquals(ACCEPT_LANGUAGE, captured.get().language());
    }

    @Test
    void echoesInboundRequestIdInResponseHeader() {
        when(useCase.execute(any())).thenReturn(Mono.just(new ReferenceDataCountriesResult(List.of(), List.of())));

        client.get()
                .uri("/api/v1/reference-data/countries")
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, ACCOUNT_REF)
                .header(RequestCorrelation.REQUEST_ID_HEADER, REQUEST_ID)
                .header(RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, ACCEPT_LANGUAGE)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(RequestCorrelation.REQUEST_ID_HEADER, REQUEST_ID);
    }

    @Test
    void missingAccountRefReturnsStandardizedErrorEnvelope() {
        client.get()
                .uri("/api/v1/reference-data/countries")
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header(RequestCorrelation.REQUEST_ID_HEADER, REQUEST_ID)
                .header(RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, ACCEPT_LANGUAGE)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().valueEquals(RequestCorrelation.REQUEST_ID_HEADER, REQUEST_ID)
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo(ErrorCodes.MEMBER_CONTEXT_INVALID)
                .jsonPath("$.message").isEqualTo("Member context is invalid.")
                .jsonPath("$.requestId").doesNotExist();
    }

    @Test
    void blankAccountRefReturnsStandardizedErrorEnvelope() {
        client.get()
                .uri("/api/v1/reference-data/countries")
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, "   ")
                .header(RequestCorrelation.REQUEST_ID_HEADER, REQUEST_ID)
                .header(RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, ACCEPT_LANGUAGE)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().valueEquals(RequestCorrelation.REQUEST_ID_HEADER, REQUEST_ID)
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo(ErrorCodes.MEMBER_CONTEXT_INVALID)
                .jsonPath("$.message").isEqualTo("Member context is invalid.")
                .jsonPath("$.requestId").doesNotExist();
    }

    @Test
    void sendsNormalizedLanguageToUseCaseWhenHeaderProvided() {
        when(useCase.execute(any())).thenReturn(Mono.just(new ReferenceDataCountriesResult(List.of(), List.of())));

        client.get()
                .uri("/api/v1/reference-data/countries")
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, ACCOUNT_REF)
                .header(RequestCorrelation.REQUEST_ID_HEADER, REQUEST_ID)
                .header(RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, "en-US")
                .exchange()
                .expectStatus().isOk();

        ArgumentCaptor<GetReferenceDataCountriesCommand> captor =
                ArgumentCaptor.forClass(GetReferenceDataCountriesCommand.class);
        verify(useCase).execute(captor.capture());
        assertEquals(Locale.ENGLISH.getLanguage(), captor.getValue().language());
    }

    private static RequestLoggingProperties requestLoggingProperties() {
        RequestLoggingProperties properties = new RequestLoggingProperties();
        properties.setEnabled(true);
        properties.setLogHeaders(false);
        return properties;
    }
}
