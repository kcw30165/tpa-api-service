package com.bct.ngtpa.apiservice.application.dto.accountgainloss;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.time.LocalDate;

public record FetchAccountGainLossCommand(
    AccountGainLossPeriod period,
    String accountEnv,
    String policyNo,
    String certNo,
    String actorUserId,
    ActorUserRole actorUserRole,
    LocalDate referenceDate) {}
