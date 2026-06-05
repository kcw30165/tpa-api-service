package com.bct.ngtpa.apiservice.adapter.in.web.response;

/**
 * Endpoint-specific result payload for personal-information update.
 */
public record PersonalInformationUpdateResultResponse(
        String refNo,
        String submitDate,
        String submitTime) {
}
