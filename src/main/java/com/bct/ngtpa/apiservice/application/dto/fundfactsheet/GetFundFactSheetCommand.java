package com.bct.ngtpa.apiservice.application.dto.fundfactsheet;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;

/**
 * Input of the fund fact sheet use case. The values come from the trusted portal context. Nothing
 * is validated here: the focused fetch command validates its own fields inside the reactive chain,
 * so any invalid value surfaces as the single generic retrieval failure. The command carries no
 * language, because language selection belongs to the web mapper.
 */
public record GetFundFactSheetCommand(
    String accountEnv, String policyNo, ActorUserRole actorUserRole) {}
