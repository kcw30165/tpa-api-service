package com.bct.ngtpa.apiservice.application.dto.fundfactsheet;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;

/**
 * Input of the fund fact sheet use case. The values come from the trusted portal context. The
 * record validates nothing itself: the use case checks that the policy number, the certificate
 * number and the scheme type are present, and the focused fetch command validates its own fields
 * inside the reactive chain, so any invalid value surfaces as the single generic retrieval
 * failure. The command carries no language, because language selection belongs to the web mapper.
 */
public record GetFundFactSheetCommand(
    String accountEnv,
    String policyNo,
    String certNo,
    String schemeType,
    ActorUserRole actorUserRole) {}
