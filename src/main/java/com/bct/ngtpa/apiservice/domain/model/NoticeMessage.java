package com.bct.ngtpa.apiservice.domain.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Core domain entity representing a single Notice Board / Ring Bell message.
 * All state lives in Progress (via APIM). Fields marked TBC are not yet returned
 * by the APIM and will be populated once the APIM contract is extended.
 */
public record NoticeMessage(
        String msgCode,
        String msgCodeLong,
        Integer seq,
        String category,
        MessageType msgType,
        String msgTitle,
        String msgContentChi,
        String msgContentEng,
        boolean isRead,
        LocalDateTime startDatetime,    // Parsed from APIM start-datetime string in adapter/out/apim
        LocalDateTime endDatetime,      // Parsed from APIM end-datetime string once APIM returns it
        MessageStatus msgStatus,
        AudienceType targetAudience,    // TBC: not yet returned by APIM
        String triggerPoint,            // TBC: not yet returned by APIM
        List<Hyperlink> hyperlinks      // TBC: not yet returned by APIM
) {
    /**
     * A message is expired when its end datetime has passed.
     * Messages with no end datetime never expire.
     */
    public boolean isExpired() {
        return isExpired(LocalDateTime.now());
    }

    public boolean isExpired(LocalDateTime now) {
        return endDatetime != null && now != null && now.isAfter(endDatetime);
    }

    /**
     * A message is visible when it has started and has not yet expired.
     * Messages with no start datetime are considered invisible.
     */
    public boolean isVisible() {
        return isVisible(LocalDateTime.now());
    }

    public boolean isVisible(LocalDateTime now) {
        if (startDatetime == null || now == null || isExpired(now)) {
            return false;
        }
        return !now.isBefore(startDatetime);
    }
}
