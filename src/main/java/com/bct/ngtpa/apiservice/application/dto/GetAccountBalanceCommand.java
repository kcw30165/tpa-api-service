package com.bct.ngtpa.apiservice.application.dto;

public record GetAccountBalanceCommand(
    String language,
    String currency,
    String accountEnv,
    String policyNo,
    String certNo,
    String trustCode,
    String schemeType,
    String actorUserId,
    ActorUserRole actorUserRole) {}
