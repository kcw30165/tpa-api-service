package com.bct.ngtpa.apiservice.application.dto;

/**
 * Explicit authenticated retrieval input. All selected-account values are trusted values copied by the inbound web boundary.
 */
public record GetPersonalInformationCommand(
        String language,
        String accountEnv,
        String policyNo,
        String certNo,
        String actorUserId,
        ActorUserRole actorUserRole) {

    /** Compatibility constructor for existing callers and tests. Production web delivery uses the explicit-role constructor. */

}
