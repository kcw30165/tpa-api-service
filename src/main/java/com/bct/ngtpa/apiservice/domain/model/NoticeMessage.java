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
        MessageType msgType,
        String msgContentChi,
        String msgContentEng,
        LocalDateTime startDatetime,
        LocalDateTime endDatetime,      // TBC: not yet returned by APIM
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
        return endDatetime != null && LocalDateTime.now().isAfter(endDatetime);
    }

    /**
     * A message is visible when it has started and has not yet expired.
     * Messages with no start datetime are considered already started.
     */
    public boolean isVisible() {
        if (isExpired()) {
            return false;
        }
        return startDatetime == null || !LocalDateTime.now().isBefore(startDatetime);
    }
}
