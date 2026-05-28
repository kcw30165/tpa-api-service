package com.bct.ngtpa.apiservice.application.dto;

import java.time.LocalDate;

public record AccountContext(
        String accountRef,
        String accountEnv,
        String policyNo,
        String certNo,
        String trustCode,
        String schemeType,
        TermStatus termStatus,
        LocalDate termCompletionDate
) {}
