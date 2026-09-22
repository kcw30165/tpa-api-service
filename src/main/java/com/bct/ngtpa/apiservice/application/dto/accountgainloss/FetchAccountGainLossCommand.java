package com.bct.ngtpa.apiservice.application.dto.accountgainloss;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.time.LocalDate;

/**
 * Currency is nullable and unnormalized, mirroring {@code FetchAccountBalanceCommand}. The single
 * production caller is {@code GetAccountGainLossService}; no compatibility constructor is added
 * because nothing else constructs this record.
 */
public record FetchAccountGainLossCommand(
    AccountGainLossPeriod period,
    String currency,
    String accountEnv,
    String policyNo,
    String certNo,
    String actorUserId,
    ActorUserRole actorUserRole,
    LocalDate referenceDate) {}
