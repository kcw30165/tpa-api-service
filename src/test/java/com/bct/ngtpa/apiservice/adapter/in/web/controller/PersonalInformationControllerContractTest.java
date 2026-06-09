package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingWebFilter;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.response.FormPageResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.FormSchemaResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.PageResponse;
import com.bct.ngtpa.apiservice.application.dto.GetPersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.dto.PersonalInformationResult;
import com.bct.ngtpa.apiservice.application.port.in.GetPersonalInformationUseCase;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizerProperties;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class PersonalInformationControllerContractTest {

    private static final String ACCOUNT_REF = "ACC-123";
    private static final String REQUEST_ID = "client-request-uuid";
    private static final String ACCEPT_LANGUAGE = "en";

    private GetPersonalInformationUseCase useCase;
    private PersonalInformationWebMapper mapper;
    private ApiExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        useCase = Mockito.mock(GetPersonalInformationUseCase.class);
        mapper = Mockito.mock(PersonalInformationWebMapper.class);

        exceptionHandler = new ApiExceptionHandler(
                (errorCode, locale, accountEnv, trustCode, schemeType) -> {
                    if (ErrorCodes.MEMBER_CONTEXT_INVALID.equals(errorCode)) {
                        return "Member context is invalid.";
                    }
                    return "Unexpected error.";
                },
                new LoggingSanitizer(new ObjectMapper(), new LoggingSanitizerProperties()));
    }

    @Test
    void getPersonalInformationReturns200WithGenericFormPageResponse() {
        var result = new PersonalInformationResult(Map.of("addr1", "ABC Street"), Map.of("addr1", "EDITABLE_COM"));
        when(useCase.execute(any())).thenReturn(Mono.just(result));
        when(mapper.toFormPageResponse(eq(result), eq(ACCEPT_LANGUAGE)))
                .thenReturn(response(ACCEPT_LANGUAGE, "Personal Information"));

        client().get()
                .uri("/api/v1/personal-information")
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, ACCOUNT_REF)
                .header(RequestCorrelation.REQUEST_ID_HEADER, REQUEST_ID)
                .header(RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, ACCEPT_LANGUAGE)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(RequestCorrelation.REQUEST_ID_HEADER, REQUEST_ID)
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.status").isEqualTo("SUCCESS")
                .jsonPath("$.page.id").isEqualTo("personalInformationPage")
                .jsonPath("$.page.title").isEqualTo("Personal Information")
                .jsonPath("$.page.lang").isEqualTo(ACCEPT_LANGUAGE)
                .jsonPath("$.form.id").isEqualTo("personalInformationForm")
                .jsonPath("$.form.version").isEqualTo("1.0")
                .jsonPath("$.form.mode").isEqualTo("view")
                .jsonPath("$.messages").isArray()
                .jsonPath("$.errors").isArray();
    }

    @Test
    void getPersonalInformationPassesAccountRefAndLanguageToUseCase() {
        AtomicReference<GetPersonalInformationCommand> captured = new AtomicReference<>();
        var result = new PersonalInformationResult(Map.of(), Map.of());
        when(useCase.execute(any())).thenAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return Mono.just(result);
        });
        when(mapper.toFormPageResponse(eq(result), eq("zh_HK"))).thenReturn(response("zh_HK", "個人資料"));

        client().get()
                .uri("/api/v1/personal-information")
                .header(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, ACCOUNT_REF)
                .header(RequestCorrelation.REQUEST_ID_HEADER, REQUEST_ID)
                .header(RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, "zh-HK")
                .exchange()
                .expectStatus().isOk();

        assertEquals(ACCOUNT_REF, captured.get().accountRef());
        assertEquals("zh_HK", captured.get().language());
    }

    @Test
    void getPersonalInformationReturnsGenericBaseErrorWhenAccountRefMissing() {
        client().get()
                .uri("/api/v1/personal-information")
                .header(RequestCorrelation.REQUEST_ID_HEADER, REQUEST_ID)
                .header(RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, ACCEPT_LANGUAGE)
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

    @Test
    void getPersonalInformationDelegatesToWebMapperAfterUseCase() {
        var result = new PersonalInformationResult(Map.of("email", "nick@example.com"), Map.of("email", "READONLY"));
        when(useCase.execute(any())).thenReturn(Mono.just(result));
        when(mapper.toFormPageResponse(eq(result), eq(ACCEPT_LANGUAGE))).thenReturn(response(ACCEPT_LANGUAGE, "Personal Information"));

        client().get()
                .uri("/api/v1/personal-information")
                .header(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, ACCOUNT_REF)
                .header(RequestCorrelation.REQUEST_ID_HEADER, REQUEST_ID)
                .header(RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, ACCEPT_LANGUAGE)
                .exchange()
                .expectStatus().isOk();

        ArgumentCaptor<GetPersonalInformationCommand> commandCaptor = ArgumentCaptor.forClass(GetPersonalInformationCommand.class);
        verify(useCase).execute(commandCaptor.capture());
        verify(mapper).toFormPageResponse(result, ACCEPT_LANGUAGE);
        assertEquals(ACCOUNT_REF, commandCaptor.getValue().accountRef());
    }

    private WebTestClient client() {
        return WebTestClient.bindToController(new PersonalInformationController(useCase, mapper))
                .controllerAdvice(exceptionHandler)
                .webFilter(new RequestLoggingWebFilter(
                        requestLoggingProperties(),
                        new LoggingSanitizer(new ObjectMapper(), new LoggingSanitizerProperties()),
                        new ObjectMapper()))
                .build();
    }

    private RequestLoggingProperties requestLoggingProperties() {
        RequestLoggingProperties properties = new RequestLoggingProperties();
        properties.setEnabled(true);
        properties.setLogHeaders(false);
        return properties;
    }

    private FormPageResponse<FormSchemaResponse> response(String language, String title) {
        return FormPageResponse.success(
                new PageResponse("personalInformationPage", title, language),
                new FormSchemaResponse("personalInformationForm", "1.0", "view", null, null, null, null));
    }
}
