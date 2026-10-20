package com.bct.ngtpa.apiservice.adapter.in.web.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NotificationDto(
    String msgCode,
    String sequence,
    String category,
    String msgContent,
    @JsonProperty("isRead") boolean isRead,
    String startDateTime) {}
