package com.bct.ngtpa.apiservice.application.dto;

import java.time.LocalDate;

public record FetchAccountBalanceCommand(
    String language,
    String currency,
    String accountEnv,
    String policyNo,
    String certNo,
    String trustCode,
    String schemeType,
    String loginUserId,
    ActorUserRole actorUserRole,
    LocalDate referenceDate) {}
