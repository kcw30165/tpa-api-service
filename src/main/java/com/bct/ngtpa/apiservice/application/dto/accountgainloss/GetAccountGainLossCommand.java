package com.bct.ngtpa.apiservice.application.dto.accountgainloss;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;

/**
 * Currency is nullable and unnormalized here, matching {@code GetAccountBalanceCommand}: null means
 * "not specified". Only the APIM request boundary ({@code TrpGetInvestGlApimRequest}) normalizes
 * null to the wire empty-string form; this command layer never fabricates a value.
 *
 * <p>The nine-argument compatibility constructor below is the exact shape {@code
 * AccountGainLossController} uses for the standalone {@code /api/v1/account/gain-loss} endpoint,
 * which has no currency concept in its public contract and is unaffected by this addition. It
 * forwards {@code currency = null}.
 */
public record GetAccountGainLossCommand(
    String language,
    AccountGainLossPeriod period,
    String currency,
    String accountEnv,
    String policyNo,
    String certNo,
    String trustCode,
    String schemeType,
    String actorUserId,
    ActorUserRole actorUserRole) {

  public GetAccountGainLossCommand(
      String language,
      AccountGainLossPeriod period,
      String accountEnv,
      String policyNo,
      String certNo,
      String trustCode,
      String schemeType,
      String actorUserId,
      ActorUserRole actorUserRole) {
    this(
        language,
        period,
        null,
        accountEnv,
        policyNo,
        certNo,
        trustCode,
        schemeType,
        actorUserId,
        actorUserRole);
  }
}
