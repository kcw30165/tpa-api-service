package com.bct.ngtpa.apiservice.application.dto.accountsummary;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.accountgainloss.AccountGainLossPeriod;

/**
 * Carries everything both the Account Balance and Account Gain/Loss application services need, so
 * the summary use case can resolve one reference date and one metadata set and compose both
 * existing assemblers without re-deriving their inputs.
 */
public record GetAccountSummaryCommand(
    String language,
    String currency,
    AccountGainLossPeriod period,
    String accountEnv,
    String policyNo,
    String certNo,
    String trustCode,
    String schemeType,
    String actorUserId,
    ActorUserRole actorUserRole) {}
