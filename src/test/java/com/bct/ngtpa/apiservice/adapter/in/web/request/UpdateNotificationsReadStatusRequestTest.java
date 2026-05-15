package com.bct.ngtpa.apiservice.adapter.in.web.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class UpdateNotificationsReadStatusRequestTest {

    @Test
    void keepsNotificationIdsNullWhenInputIsNull() {
        UpdateNotificationsReadStatusRequest request = new UpdateNotificationsReadStatusRequest(null);

        assertNull(request.notificationId());
    }

    @Test
    void defensivelyCopiesNotificationIds() {
        List<String> ids = new ArrayList<>(List.of("n1", "n2"));

        UpdateNotificationsReadStatusRequest request = new UpdateNotificationsReadStatusRequest(ids);
        ids.add("n3");

        assertEquals(List.of("n1", "n2"), request.notificationId());
        assertThrows(UnsupportedOperationException.class, () -> request.notificationId().add("n4"));
    }
}