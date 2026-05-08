package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusResult;
import com.bct.ngtpa.apiservice.domain.model.MessageStatus;
import com.bct.ngtpa.apiservice.domain.model.NotificationReadStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationReadStatusWebMapperTest {

    private final NotificationReadStatusWebMapper mapper = new NotificationReadStatusWebMapper();

    @Test
    void toResponseMapsMsgCodeCorrectly() {
        var result = new UpdateNotificationsReadStatusResult(List.of(
                new NotificationReadStatus("code-01", MessageStatus.READ, true)));

        var response = mapper.toResponse(result);

        assertEquals("code-01", response.notifications().getFirst().msgCode());
    }

    @Test
    void toResponseReturnsIsReadTrueWhenStatusReadAndSuccessTrue() {
        var result = new UpdateNotificationsReadStatusResult(List.of(
                new NotificationReadStatus("code-01", MessageStatus.READ, true)));

        var response = mapper.toResponse(result);

        assertTrue(response.notifications().getFirst().isRead());
    }

    @Test
    void toResponseReturnsIsReadFalseWhenStatusReadButSuccessFalse() {
        var result = new UpdateNotificationsReadStatusResult(List.of(
                new NotificationReadStatus("code-01", MessageStatus.READ, false)));

        var response = mapper.toResponse(result);

        assertFalse(response.notifications().getFirst().isRead());
    }

    @Test
    void toResponseMapsAllNotificationsInOrder() {
        var result = new UpdateNotificationsReadStatusResult(List.of(
                new NotificationReadStatus("code-01", MessageStatus.READ, true),
                new NotificationReadStatus("code-02", MessageStatus.READ, false)));

        var response = mapper.toResponse(result);

        assertEquals(2, response.notifications().size());
        assertEquals("code-01", response.notifications().get(0).msgCode());
        assertTrue(response.notifications().get(0).isRead());
        assertEquals("code-02", response.notifications().get(1).msgCode());
        assertFalse(response.notifications().get(1).isRead());
    }
}
