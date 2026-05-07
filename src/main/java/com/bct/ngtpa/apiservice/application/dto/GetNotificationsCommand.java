package com.bct.ngtpa.apiservice.application.dto;

public record GetNotificationsCommand(
        String env,
        String mbrType,
        Integer page,
        Integer size,
        String dateFormat,
        String timezone,
        String policyNo,
        String certNo,
        String userId,
        String refDate
) {}
