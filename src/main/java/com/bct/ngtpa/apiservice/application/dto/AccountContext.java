package com.bct.ngtpa.apiservice.application.dto;

public record AccountContext(
        String accountRef,
        String accountEnv,
        String policyNo,
        String certNo,
        String trustCode,
        String schemeType
) {}
