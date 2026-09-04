package com.bct.ngtpa.apiservice.application.dto.accountgainloss;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;

public record GetAccountGainLossCommand(
    String language,
    AccountGainLossPeriod period,
    String accountEnv,
    String policyNo,
    String certNo,
    String trustCode,
    String schemeType,
    String actorUserId,
    ActorUserRole actorUserRole) {}
