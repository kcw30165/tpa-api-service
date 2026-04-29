package com.bct.ngtpa.apiservice.adapter.in.web;

import com.bct.ngtpa.apiservice.application.dto.GetNotificationsCommand;
import com.bct.ngtpa.apiservice.application.dto.NotificationDateOptions;
import com.bct.ngtpa.apiservice.application.dto.NotificationListResult;
import com.bct.ngtpa.apiservice.application.exception.InvalidNotificationRequestException;
import com.bct.ngtpa.apiservice.application.port.in.GetNotificationsUseCase;
import com.bct.ngtpa.apiservice.domain.model.AudienceType;
import com.bct.ngtpa.apiservice.domain.model.Hyperlink;
import com.bct.ngtpa.apiservice.domain.model.MessageStatus;
import com.bct.ngtpa.apiservice.domain.model.MessageType;
import com.bct.ngtpa.apiservice.domain.model.NoticeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationControllerTest {

    @Test
    void acceptsNewQueryParamsAndBuildsFrontendResponse() {
        AtomicReference<GetNotificationsCommand> captured = new AtomicReference<>();
        GetNotificationsUseCase useCase = command -> {
            captured.set(command);
            return Mono.just(new NotificationListResult(
                    List.of(notification("ACT_REQ", "LONG-001", false, LocalDateTime.of(2026, 4, 29, 14, 15))),
                    NotificationDateOptions.resolve(command.dateFormat(), command.timezone())));
        };

        WebTestClient client = WebTestClient.bindToController(new NotificationController(useCase))
                .controllerAdvice(new ApiExceptionHandler())
                .build();

        client.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/notifications")
                        .queryParam("env", "DEV")
                        .queryParam("mbrType", "MBR")
                        .queryParam("page", 1)
                        .queryParam("size", 20)
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

        assertEquals("DEV", captured.get().env());
        assertEquals("MBR", captured.get().mbrType());
        assertEquals(1, captured.get().page());
        assertEquals(20, captured.get().size());
        assertEquals("dd/MM/yyyy HH:mm", captured.get().dateFormat());
        assertEquals("Asia/Hong_Kong", captured.get().timezone());
    }

    @Test
    void acceptsLegacyQueryParamNamesForBackwardCompatibility() {
        AtomicReference<GetNotificationsCommand> captured = new AtomicReference<>();
        GetNotificationsUseCase useCase = command -> {
            captured.set(command);
            return Mono.just(new NotificationListResult(List.of(), NotificationDateOptions.defaults()));
        };

        WebTestClient client = WebTestClient.bindToController(new NotificationController(useCase))
                .controllerAdvice(new ApiExceptionHandler())
                .build();

        client.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/notifications")
                        .queryParam("env", "UAT")
                        .queryParam("mbrType", "VIP")
                        .build())
                .exchange()
                .expectStatus().isOk();

        assertEquals("UAT", captured.get().env());
        assertEquals("VIP", captured.get().mbrType());
    }

    @Test
    void returnsStandardizedErrorForInvalidTimezone() {
        GetNotificationsUseCase useCase = command -> Mono.error(
                new InvalidNotificationRequestException("Invalid timezone: Mars/Olympus"));

        WebTestClient client = WebTestClient.bindToController(new NotificationController(useCase))
                .controllerAdvice(new ApiExceptionHandler())
                .build();

        client.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/notifications")
                        .queryParam("env", "DEV")
                        .queryParam("mbrType", "MBR")
                        .queryParam("timezone", "Mars/Olympus")
                        .build())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("400")
                .jsonPath("$.message").isEqualTo("Invalid timezone: Mars/Olympus")
                .jsonPath("$.timestamp").exists();
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
}