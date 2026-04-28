package com.bct.ngtpa.apiservice.application.dto;

public record GetNotificationsCommand(
        String environment,
        String memberType,
        String policyNo,
        String certNo,
        String userId,
        String refDate
) {}
