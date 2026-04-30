package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetMessageBoardApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetMessageBoardDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetMessageBoardMessageItem;
import com.bct.ngtpa.apiservice.application.dto.GetNotificationsCommand;
import com.bct.ngtpa.apiservice.application.dto.NotificationListResult;
import com.bct.ngtpa.apiservice.config.ApimProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApimNoticeMessageAdapterTest {

    private final ApimNoticeMessageAdapter adapter = new ApimNoticeMessageAdapter(
            null, null, null, new ApimProperties());

    @Test
    void mapsApimFieldsToDomainModelUsingCategoryAndStatus() {
        GetMessageBoardMessageItem messageItem = GetMessageBoardMessageItem.builder()
                .msgCode("SHORT-001")
                .msgCodeLong("LONG-001")
                .seq(7)
                .msgCate("IMP_NOTE")
                .msgTitle("Should be ignored")
                .msgContentChi("chi")
                .msgContentEng("eng")
                .startDatetime("29/04/2026 14:15:00")
                .msgStatus("R")
                .isRead(false)
                .build();

        ApimResponseEnvelope<GetMessageBoardDataItem> response = ApimResponseEnvelope.<GetMessageBoardDataItem>builder()
                .response(ApimResponseBody.<GetMessageBoardDataItem>builder()
                        .data(List.of(GetMessageBoardDataItem.builder()
                                .page(1)
                                .size(20)
                                .message(List.of(messageItem))
                                .build()))
                        .build())
                .build();

        NotificationListResult result = (NotificationListResult) ReflectionTestUtils.invokeMethod(
                adapter, "toNotificationListResult", response);

        assertEquals(1, result.notifications().size());
        assertEquals("SHORT-001", result.notifications().getFirst().msgCode());
        assertEquals("LONG-001", result.notifications().getFirst().msgCodeLong());
        assertEquals("IMP_NOTE", result.notifications().getFirst().category());
        assertEquals("IMPORTANT NOTICE", result.notifications().getFirst().msgTitle());
        assertTrue(result.notifications().getFirst().isRead());
    }

        @Test
        void parsesApimDateTimeWithoutSeconds() {
                LocalDateTime parsed = (LocalDateTime) ReflectionTestUtils.invokeMethod(
                                adapter, "parseDateTime", "11/10/2023 00:00");

                assertNotNull(parsed);
                assertEquals(LocalDateTime.of(2023, 10, 11, 0, 0), parsed);
        }

    @Test
    void doesNotIncludePageOrSizeInApimRequestPayload() throws Exception {
        GetNotificationsCommand command = new GetNotificationsCommand(
                "DEV", "MBR", 1, 20, "dd/MM/yyyy HH:mm", "Asia/Hong_Kong", "P1", "C1", "U1", "29/04/2026");

        GetMessageBoardApimRequest request = (GetMessageBoardApimRequest) ReflectionTestUtils.invokeMethod(
                adapter, "toApimRequest", command);
        String json = new ObjectMapper().writeValueAsString(request);

        assertTrue(json.contains("\"env\":\"DEV\""));
        assertTrue(json.contains("\"mbr-type\":\"MBR\""));
        assertFalse(json.contains("page"));
        assertFalse(json.contains("size"));
    }
}