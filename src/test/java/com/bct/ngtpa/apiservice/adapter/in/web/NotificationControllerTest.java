package com.bct.ngtpa.apiservice.adapter.in.web;

import com.bct.ngtpa.apiservice.adapter.in.web.mapper.NotificationReadStatusWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.NotificationWebMapper;
import com.bct.ngtpa.apiservice.application.dto.GetNotificationsCommand;
import com.bct.ngtpa.apiservice.application.dto.NotificationDateOptions;
import com.bct.ngtpa.apiservice.application.dto.NotificationListResult;
import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusResult;
import com.bct.ngtpa.apiservice.application.exception.ApplicationException;
import com.bct.ngtpa.apiservice.application.exception.InvalidNotificationRequestException;
import com.bct.ngtpa.apiservice.application.port.in.GetNotificationsUseCase;
import com.bct.ngtpa.apiservice.application.port.in.UpdateNotificationsReadStatusUseCase;
import com.bct.ngtpa.apiservice.domain.model.AudienceType;
import com.bct.ngtpa.apiservice.domain.model.Hyperlink;
import com.bct.ngtpa.apiservice.domain.model.MessageStatus;
import com.bct.ngtpa.apiservice.domain.model.NotificationReadStatus;
import com.bct.ngtpa.apiservice.domain.model.MessageType;
import com.bct.ngtpa.apiservice.domain.model.NoticeMessage;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizerProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingWebFilter;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NotificationControllerTest {

        private static final String VALID_ACCOUNT_REF = "ACC-123";

    @Test
    void acceptsNewQueryParamsAndBuildsFrontendResponse() {
        AtomicReference<GetNotificationsCommand> captured = new AtomicReference<>();
        GetNotificationsUseCase useCase = command -> {
            captured.set(command);
            return Mono.just(new NotificationListResult(
                    List.of(notification("ACT_REQ", "LONG-001", false, LocalDateTime.of(2026, 4, 29, 14, 15))),
                    NotificationDateOptions.resolve(command.dateFormat(), command.timezone())));
        };

        WebTestClient client = webClient(useCase, unusedUpdateNotificationsReadStatusUseCase());

        client.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/notifications")
                        .queryParam("page", 1)
                        .queryParam("size", 99999)
                        .queryParam("dateFormat", "dd/MM/yyyy HH:mm")
                        .queryParam("timezone", "Asia/Hong_Kong")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.notifications[0].msgCode").isEqualTo("LONG-001")
                .jsonPath("$.notifications[0].sequence").isEqualTo("1")
                .jsonPath("$.notifications[0].category").isEqualTo("ACT_REQ")
                .jsonPath("$.notifications[0].msgTitle").isEqualTo("ACTION REQUIRED")
                .jsonPath("$.notifications[0].isRead").isEqualTo(false)
                .jsonPath("$.notifications[0].startDateTime").isEqualTo("29/04/2026 14:15");

        assertEquals(1, captured.get().page());
        assertEquals(99999, captured.get().size());
        assertEquals("dd/MM/yyyy HH:mm", captured.get().dateFormat());
        assertEquals("Asia/Hong_Kong", captured.get().timezone());
    }

        @Test
        void passesAccountRefFromHeaderContextToGetNotificationsUseCase() {
                AtomicReference<GetNotificationsCommand> captured = new AtomicReference<>();
                GetNotificationsUseCase useCase = command -> {
                        captured.set(command);
                        if (!VALID_ACCOUNT_REF.equals(command.accountRef())) {
                                return Mono.error(new ApplicationException(
                                                ErrorCodes.MEMBER_CONTEXT_INVALID,
                                                "accountRef must be present"));
                        }
                        return Mono.just(new NotificationListResult(List.of(), NotificationDateOptions.defaults()));
                };

                webClientWithRequestLoggingFilter(useCase, unusedUpdateNotificationsReadStatusUseCase())
                                .get()
                                .uri(uriBuilder -> uriBuilder.path("/api/v1/notifications").build())
                                .header(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, VALID_ACCOUNT_REF)
                                .exchange()
                                .expectStatus().isOk();

                assertEquals(VALID_ACCOUNT_REF, captured.get().accountRef());
        }

        @Test
        void missingAccountRefReturnsMemberContextInvalidForGetNotifications() {
                GetNotificationsUseCase useCase = command -> Mono.error(new ApplicationException(
                                ErrorCodes.MEMBER_CONTEXT_INVALID,
                                "accountRef must be present"));

                webClientWithRequestLoggingFilterWithoutDefaultAccountRef(useCase, unusedUpdateNotificationsReadStatusUseCase())
                                .get()
                                .uri(uriBuilder -> uriBuilder.path("/api/v1/notifications").build())
                                .exchange()
                                .expectStatus().isBadRequest()
                                .expectHeader().valueMatches("X-Request-Id",
                                                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                                .expectBody()
                                .jsonPath("$.errorCode").isEqualTo(ErrorCodes.MEMBER_CONTEXT_INVALID)
                                .jsonPath("$.message").isEqualTo("Member context is invalid.")
                                .jsonPath("$.requestId").doesNotExist();
        }

                    @Test
                    void invalidAccountRefReturnsMemberContextInvalidForGetNotifications() {
                        GetNotificationsUseCase useCase = command -> Mono.error(new ApplicationException(
                                ErrorCodes.MEMBER_CONTEXT_INVALID,
                                "accountRef is invalid"));

                        webClientWithRequestLoggingFilterWithoutDefaultAccountRef(useCase, unusedUpdateNotificationsReadStatusUseCase())
                                .get()
                                .uri(uriBuilder -> uriBuilder.path("/api/v1/notifications").build())
                                .header(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, "BAD-999")
                                .exchange()
                                .expectStatus().isBadRequest()
                                .expectHeader().valueMatches("X-Request-Id",
                                        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                                .expectBody()
                                .jsonPath("$.errorCode").isEqualTo(ErrorCodes.MEMBER_CONTEXT_INVALID)
                                .jsonPath("$.message").isEqualTo("Member context is invalid.")
                                .jsonPath("$.requestId").doesNotExist();
                    }

    @Test
    void acceptsLegacyQueryParamNamesForBackwardCompatibility() {
        AtomicReference<GetNotificationsCommand> captured = new AtomicReference<>();
        GetNotificationsUseCase useCase = command -> {
            captured.set(command);
            return Mono.just(new NotificationListResult(List.of(), NotificationDateOptions.defaults()));
        };

                WebTestClient client = webClient(useCase, unusedUpdateNotificationsReadStatusUseCase());

        client.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/notifications")
                        .queryParam("env", "UAT")
                        .queryParam("mbrType", "VIP")
                        .build())
                .exchange()
                .expectStatus().isOk();

        // env and mbrType query params are no longer accepted; they are ignored by Spring MVC
        // and env/mbrType on the command are populated from PortalAccessContext by the use case
        assertNull(captured.get().accountEnv());
        assertNull(captured.get().mbrType());
    }

    @Test
    void returnsStandardizedErrorForInvalidTimezone() {
        GetNotificationsUseCase useCase = command -> Mono.error(
                new InvalidNotificationRequestException("Invalid timezone: Mars/Olympus"));

        WebTestClient client = webClient(useCase, unusedUpdateNotificationsReadStatusUseCase());

        client.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/notifications")
                        .queryParam("timezone", "Mars/Olympus")
                        .build())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo(ErrorCodes.NOTIFICATION_REQUEST_INVALID)
                .jsonPath("$.message").isEqualTo("Invalid notification request.");
    }

    @Test
    void errorResponsesIncludeGeneratedRequestIdHeaderAndKeepBodyHeaderOnly() throws Exception {
        GetNotificationsUseCase useCase = command -> Mono.error(
                new InvalidNotificationRequestException("Invalid timezone: Mars/Olympus"));

        webClientWithRequestLoggingFilter(useCase, unusedUpdateNotificationsReadStatusUseCase())
                .get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/notifications")
                        .queryParam("timezone", "Mars/Olympus")
                        .build())
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().valueMatches("X-Request-Id",
                        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo(ErrorCodes.NOTIFICATION_REQUEST_INVALID)
                .jsonPath("$.message").isEqualTo("Invalid notification request.")
                .jsonPath("$.requestId").doesNotExist()
                .consumeWith(result -> {
                                        try {
                                                var body = new ObjectMapper().readTree(result.getResponseBody());
                                                assertEquals(2, body.size());
                                        } catch (Exception exception) {
                                                throw new AssertionError(exception);
                                        }
                });
    }

    @Test
    void patchesNotificationReadStatusAndBuildsFrontendResponse() {
        AtomicReference<UpdateNotificationsReadStatusCommand> captured = new AtomicReference<>();
        UpdateNotificationsReadStatusUseCase updateUseCase = command -> {
            captured.set(command);
            return Mono.just(new UpdateNotificationsReadStatusResult(List.of(
                    new NotificationReadStatus("msgCode1", MessageStatus.READ, true),
                    new NotificationReadStatus("msgCode2", MessageStatus.READ, false)
            )));
        };

        WebTestClient client = webClient(unusedGetNotificationsUseCase(), updateUseCase);

        client.patch()
                .uri("/api/v1/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "notificationId", List.of("msgCode1", "msgCode2")))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.notifications[0].msgCode").isEqualTo("msgCode1")
                .jsonPath("$.notifications[0].isRead").isEqualTo(true)
                .jsonPath("$.notifications[1].msgCode").isEqualTo("msgCode2")
                .jsonPath("$.notifications[1].isRead").isEqualTo(false);

        assertEquals(List.of("msgCode1", "msgCode2"), captured.get().notificationIds());
    }

        @Test
        void passesAccountRefFromHeaderContextToUpdateNotificationsUseCase() {
                AtomicReference<UpdateNotificationsReadStatusCommand> captured = new AtomicReference<>();
                UpdateNotificationsReadStatusUseCase updateUseCase = command -> {
                        captured.set(command);
                        if (!VALID_ACCOUNT_REF.equals(command.accountRef())) {
                                return Mono.error(new ApplicationException(
                                                ErrorCodes.MEMBER_CONTEXT_INVALID,
                                                "accountRef must be present"));
                        }
                        return Mono.just(new UpdateNotificationsReadStatusResult(List.of()));
                };

                webClientWithRequestLoggingFilter(unusedGetNotificationsUseCase(), updateUseCase)
                                .patch()
                                .uri("/api/v1/notifications")
                                .header(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, VALID_ACCOUNT_REF)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Map.of("notificationId", List.of("msgCode1")))
                                .exchange()
                                .expectStatus().isOk();

                assertEquals(VALID_ACCOUNT_REF, captured.get().accountRef());
        }

        @Test
        void invalidAccountRefReturnsMemberContextInvalidForUpdateNotifications() {
                UpdateNotificationsReadStatusUseCase updateUseCase = command -> Mono.error(new ApplicationException(
                                ErrorCodes.MEMBER_CONTEXT_INVALID,
                                "accountRef is invalid"));

                webClientWithRequestLoggingFilterWithoutDefaultAccountRef(unusedGetNotificationsUseCase(), updateUseCase)
                                .patch()
                                .uri("/api/v1/notifications")
                                .header(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, "BAD-999")
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Map.of("notificationId", List.of("msgCode1")))
                                .exchange()
                                .expectStatus().isBadRequest()
                                .expectHeader().valueMatches("X-Request-Id",
                                                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                                .expectBody()
                                .jsonPath("$.errorCode").isEqualTo(ErrorCodes.MEMBER_CONTEXT_INVALID)
                                .jsonPath("$.message").isEqualTo("Member context is invalid.")
                                .jsonPath("$.requestId").doesNotExist();
        }

                    @Test
                    void missingAccountRefReturnsMemberContextInvalidForUpdateNotifications() {
                        UpdateNotificationsReadStatusUseCase updateUseCase = command -> Mono.error(new ApplicationException(
                                ErrorCodes.MEMBER_CONTEXT_INVALID,
                                "accountRef must be present"));

                        webClientWithRequestLoggingFilterWithoutDefaultAccountRef(unusedGetNotificationsUseCase(), updateUseCase)
                                .patch()
                                .uri("/api/v1/notifications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Map.of("notificationId", List.of("msgCode1")))
                                .exchange()
                                .expectStatus().isBadRequest()
                                .expectHeader().valueMatches("X-Request-Id",
                                        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                                .expectBody()
                                .jsonPath("$.errorCode").isEqualTo(ErrorCodes.MEMBER_CONTEXT_INVALID)
                                .jsonPath("$.message").isEqualTo("Member context is invalid.")
                                .jsonPath("$.requestId").doesNotExist();
                    }

    @Test
    void rejectsPatchRequestWhenNotificationIdIsEmpty() {
        assertInvalidPatchRequest(
                Map.of(
                        "notificationId", List.of()),
                "notificationId must not be empty");
    }

    @Test
    void rejectsPatchRequestWhenNotificationIdContainsBlankValues() {
        assertInvalidPatchRequest(
                Map.of(
                        "notificationId", List.of(" ")),
                "notificationId must not contain blank values");
    }

        private void assertInvalidPatchRequest(Object requestBody, String expectedMessage) {
        webClient(unusedGetNotificationsUseCase(), unusedUpdateNotificationsReadStatusUseCase())
                .patch()
                .uri("/api/v1/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo(ErrorCodes.REQUEST_VALIDATION_FAILED)
                .jsonPath("$.message").isEqualTo("Invalid request payload.");
    }

    private WebTestClient webClient(
            GetNotificationsUseCase getNotificationsUseCase,
            UpdateNotificationsReadStatusUseCase updateNotificationsReadStatusUseCase) {
        return buildNotificationClient(getNotificationsUseCase, updateNotificationsReadStatusUseCase, true);
    }

    private WebTestClient webClientWithoutDefaultAccountRef(
            GetNotificationsUseCase getNotificationsUseCase,
            UpdateNotificationsReadStatusUseCase updateNotificationsReadStatusUseCase) {
        return buildNotificationClient(getNotificationsUseCase, updateNotificationsReadStatusUseCase, false);
    }

    private WebTestClient buildNotificationClient(
            GetNotificationsUseCase getNotificationsUseCase,
            UpdateNotificationsReadStatusUseCase updateNotificationsReadStatusUseCase,
            boolean withDefaultAccountRef) {
        var client = WebTestClient.bindToController(new NotificationController(
                        getNotificationsUseCase,
                        updateNotificationsReadStatusUseCase,
                        new NotificationWebMapper(),
                        new NotificationReadStatusWebMapper()))
                .webFilter(new RequestLoggingWebFilter(new RequestLoggingProperties(), testLoggingSanitizer(), new ObjectMapper()))
                .controllerAdvice(new ApiExceptionHandler(testErrorMessageResolver(), testLoggingSanitizer()))
                .build();
        if (withDefaultAccountRef) {
            return client.mutate()
                    .defaultHeader(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, VALID_ACCOUNT_REF)
                    .build();
        }
        return client;
    }

        private WebTestClient webClientWithRequestLoggingFilter(
                        GetNotificationsUseCase getNotificationsUseCase,
                        UpdateNotificationsReadStatusUseCase updateNotificationsReadStatusUseCase) {
                return buildNotificationClient(getNotificationsUseCase, updateNotificationsReadStatusUseCase, true);
        }

        private WebTestClient webClientWithRequestLoggingFilterWithoutDefaultAccountRef(
                        GetNotificationsUseCase getNotificationsUseCase,
                        UpdateNotificationsReadStatusUseCase updateNotificationsReadStatusUseCase) {
                return buildNotificationClient(getNotificationsUseCase, updateNotificationsReadStatusUseCase, false);
        }

    private GetNotificationsUseCase unusedGetNotificationsUseCase() {
        return command -> Mono.just(new NotificationListResult(List.of(), NotificationDateOptions.defaults()));
    }

    private UpdateNotificationsReadStatusUseCase unusedUpdateNotificationsReadStatusUseCase() {
        return command -> Mono.just(new UpdateNotificationsReadStatusResult(List.of()));
    }

    private NoticeMessage notification(String category, String longCode, boolean isRead, LocalDateTime startDateTime) {
        return new NoticeMessage(
                "SHORT-001",
                longCode,
                1,
                category,
                MessageType.fromCode(category),
                MessageType.titleFor(category),
                "chi",
                "eng",
                isRead,
                startDateTime,
                null,
                isRead ? MessageStatus.READ : MessageStatus.UNREAD,
                (AudienceType) null,
                null,
                List.<Hyperlink>of()
        );
    }

        private static ErrorMessageResolver testErrorMessageResolver() {
                return (errorCode, locale, accountEnv, trustCode, schemeType) -> switch (errorCode) {
                        case ErrorCodes.NOTIFICATION_REQUEST_INVALID -> "Invalid notification request.";
                        case ErrorCodes.MEMBER_CONTEXT_INVALID -> "Member context is invalid.";
                        case ErrorCodes.REQUEST_VALIDATION_FAILED -> "JP".equals(accountEnv)
                                        ? "Invalid request payload for JP."
                                        : "Invalid request payload.";
                        case ErrorCodes.SYSTEM_UNEXPECTED -> "Sorry, this service might be interrupted. Please try again later.";
                        default -> errorCode;
                };
        }

        private static LoggingSanitizer testLoggingSanitizer() {
                LoggingSanitizerProperties properties = new LoggingSanitizerProperties();
                properties.setSensitiveTokens(List.of("policyNo", "userId", "apiKey", "token", "memberId"));
                return new LoggingSanitizer(new ObjectMapper(), properties);
        }
}