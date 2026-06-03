package com.bct.ngtpa.apiservice.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingWebFilter;
import com.bct.ngtpa.apiservice.application.dto.GetPersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.port.in.GetPersonalInformationUseCase;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizerProperties;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/**
 * Controller contract tests for Personal Information endpoint.
 *
 * These tests are TDD-first and will fail until the controller is implemented.
 */
class PersonalInformationControllerContractTest {

    private static final String ACCOUNT_REF = "ACC-123";
    private static final String REQUEST_ID = "client-request-uuid";
    private static final String ACCEPT_LANGUAGE = "en";

    private GetPersonalInformationUseCase useCase;
    private ApiExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        useCase = Mockito.mock(GetPersonalInformationUseCase.class);

        exceptionHandler = new ApiExceptionHandler(
                new com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver() {
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
    }

    @Test
    void getPersonalInformationReturns200WithPageAndForm() {
        when(useCase.execute(any())).thenReturn(Mono.just(Map.of("page", Map.of(), "form", Map.of())));

        try {
            Class<?> controllerClass = Class.forName("com.bct.ngtpa.apiservice.adapter.in.web.PersonalInformationController");
            Object controller = controllerClass.getConstructor(GetPersonalInformationUseCase.class).newInstance(useCase);

            WebTestClient client = WebTestClient.bindToController(controller)
                    .controllerAdvice(exceptionHandler)
                    .webFilter(new RequestLoggingWebFilter(requestLoggingProperties(), new LoggingSanitizer(new ObjectMapper(), new LoggingSanitizerProperties()), new ObjectMapper()))
                    .build();

            client.get()
                    .uri("/api/v1/personal-information")
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .header(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, ACCOUNT_REF)
                    .header(RequestCorrelation.REQUEST_ID_HEADER, REQUEST_ID)
                    .header(RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, ACCEPT_LANGUAGE)
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().valueEquals(RequestCorrelation.REQUEST_ID_HEADER, REQUEST_ID)
                    .expectBody()
                    .jsonPath("$.page").exists()
                    .jsonPath("$.form").exists();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void passesAccountRefAndAcceptLanguageIntoUseCaseCommand() {
        AtomicReference<GetPersonalInformationCommand> captured = new AtomicReference<>();

        when(useCase.execute(any())).thenAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return Mono.just(Map.of("page", Map.of(), "form", Map.of()));
        });

        try {
            Class<?> controllerClass = Class.forName("com.bct.ngtpa.apiservice.adapter.in.web.PersonalInformationController");
            Object controller = controllerClass.getConstructor(GetPersonalInformationUseCase.class).newInstance(useCase);

            WebTestClient client = WebTestClient.bindToController(controller)
                    .controllerAdvice(exceptionHandler)
                    .webFilter(new RequestLoggingWebFilter(requestLoggingProperties(), new LoggingSanitizer(new ObjectMapper(), new LoggingSanitizerProperties()), new ObjectMapper()))
                    .build();

            client.get()
                    .uri("/api/v1/personal-information")
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .header(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, ACCOUNT_REF)
                    .header(RequestCorrelation.REQUEST_ID_HEADER, REQUEST_ID)
                    .header(RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, ACCEPT_LANGUAGE)
                    .exchange()
                    .expectStatus().isOk();

            ArgumentCaptor<GetPersonalInformationCommand> captor = ArgumentCaptor.forClass(GetPersonalInformationCommand.class);
            verify(useCase).execute(captor.capture());
            assertEquals(ACCOUNT_REF, captor.getValue().accountRef());
            assertEquals(ACCEPT_LANGUAGE, captor.getValue().language());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void echoesInboundRequestIdInResponseHeader() {
        when(useCase.execute(any())).thenReturn(Mono.just(Map.of("page", Map.of(), "form", Map.of())));

        try {
            Class<?> controllerClass = Class.forName("com.bct.ngtpa.apiservice.adapter.in.web.PersonalInformationController");
            Object controller = controllerClass.getConstructor(GetPersonalInformationUseCase.class).newInstance(useCase);

            WebTestClient client = WebTestClient.bindToController(controller)
                    .controllerAdvice(exceptionHandler)
                    .webFilter(new RequestLoggingWebFilter(requestLoggingProperties(), new LoggingSanitizer(new ObjectMapper(), new LoggingSanitizerProperties()), new ObjectMapper()))
                    .build();

            client.get()
                    .uri("/api/v1/personal-information")
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .header(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, ACCOUNT_REF)
                    .header(RequestCorrelation.REQUEST_ID_HEADER, REQUEST_ID)
                    .header(RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, ACCEPT_LANGUAGE)
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().valueEquals(RequestCorrelation.REQUEST_ID_HEADER, REQUEST_ID);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void missingAccountRefReturnsStandardizedErrorEnvelope() {
        try {
            Class<?> controllerClass = Class.forName("com.bct.ngtpa.apiservice.adapter.in.web.PersonalInformationController");
            Object controller = controllerClass.getConstructor(GetPersonalInformationUseCase.class).newInstance(useCase);

            WebTestClient client = WebTestClient.bindToController(controller)
                    .controllerAdvice(exceptionHandler)
                    .webFilter(new RequestLoggingWebFilter(requestLoggingProperties(), new LoggingSanitizer(new ObjectMapper(), new LoggingSanitizerProperties()), new ObjectMapper()))
                    .build();

            client.get()
                    .uri("/api/v1/personal-information")
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
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static RequestLoggingProperties requestLoggingProperties() {
        RequestLoggingProperties properties = new RequestLoggingProperties();
        properties.setEnabled(true);
        properties.setLogHeaders(false);
        return properties;
    }
}
