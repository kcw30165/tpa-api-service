package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.in.web.mapper.NotificationReadStatusWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.NotificationWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.request.UpdateNotificationsReadStatusRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.NotificationListResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.UpdateNotificationsReadStatusResponse;
import com.bct.ngtpa.apiservice.application.dto.GetNotificationsCommand;
import com.bct.ngtpa.apiservice.application.dto.NotificationListResult;
import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusResult;
import com.bct.ngtpa.apiservice.application.port.in.GetNotificationsUseCase;
import com.bct.ngtpa.apiservice.application.port.in.UpdateNotificationsReadStatusUseCase;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class NotificationControllerTest {

    @Test
    void getNotificationsPassesQueryOnlyCommandWithoutAccountRef() {
        AtomicReference<GetNotificationsCommand> captured = new AtomicReference<>();
        GetNotificationsUseCase getUseCase = command -> {
            captured.set(command);
            return Mono.just(new NotificationListResult(List.of()));
        };
        UpdateNotificationsReadStatusUseCase updateUseCase = command -> Mono.just(new UpdateNotificationsReadStatusResult(List.of()));
        NotificationWebMapper notificationWebMapper = mock(NotificationWebMapper.class);
        NotificationListResponse expected = mock(NotificationListResponse.class);
        when(notificationWebMapper.toResponse(org.mockito.ArgumentMatchers.any())).thenReturn(expected);

        var actual = new NotificationController(
                getUseCase,
                updateUseCase,
                notificationWebMapper,
                mock(NotificationReadStatusWebMapper.class))
                .getNotifications(2, 50, "yyyy-MM-dd", "Asia/Hong_Kong")
                .block();

        assertEquals(expected, actual);
        assertEquals(2, captured.get().page());
        assertEquals(50, captured.get().size());
        assertEquals("yyyy-MM-dd", captured.get().dateFormat());
        assertEquals("Asia/Hong_Kong", captured.get().timezone());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void updateNotificationsPassesNotificationIdsOnlyCommandWithoutAccountRef() {
        AtomicReference<UpdateNotificationsReadStatusCommand> captured = new AtomicReference<>();
        GetNotificationsUseCase getUseCase = command -> Mono.just(new NotificationListResult(List.of()));
        UpdateNotificationsReadStatusUseCase updateUseCase = command -> {
            captured.set(command);
            return Mono.just(new UpdateNotificationsReadStatusResult(List.of()));
        };
        NotificationReadStatusWebMapper readStatusWebMapper = mock(NotificationReadStatusWebMapper.class);
        MutationResponse<UpdateNotificationsReadStatusResponse> expectedResponse = mock(MutationResponse.class);
        when(readStatusWebMapper.toResponse(org.mockito.ArgumentMatchers.any())).thenReturn(expectedResponse);

        var actual = new NotificationController(
                getUseCase,
                updateUseCase,
                mock(NotificationWebMapper.class),
                readStatusWebMapper)
                .updateNotificationsReadStatus(new UpdateNotificationsReadStatusRequest(List.of("N-1")))
                .block();

        assertEquals(expectedResponse, actual);
        assertEquals(List.of("N-1"), captured.get().notificationIds());
    }
}
