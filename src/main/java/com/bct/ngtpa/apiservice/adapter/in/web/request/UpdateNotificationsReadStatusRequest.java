package com.bct.ngtpa.apiservice.adapter.in.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record UpdateNotificationsReadStatusRequest(
        @NotBlank(message = "env must not be blank")
        String env,
        @NotBlank(message = "mbrType must not be blank")
        String mbrType,
        @NotEmpty(message = "notificationId must not be empty")
        List<@NotBlank(message = "notificationId must not contain blank values") String> notificationId
) {
    public UpdateNotificationsReadStatusRequest {
        notificationId = notificationId == null ? null : List.copyOf(notificationId);
    }
}