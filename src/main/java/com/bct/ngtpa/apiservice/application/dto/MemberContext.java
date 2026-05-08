package com.bct.ngtpa.apiservice.application.dto;

public record MemberContext(
        String policyNo,
        String certNo,
        String userId,
        String trustCode,
        String schemeType
) {}
