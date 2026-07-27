package com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency;

/** Safe metadata only. Sensitive replay material is protected elsewhere. */
public record LoginIdempotencyReplaySnapshot(String outcomeCode, String opaqueCommitRef) {
    @Override
    public String toString() {
        return "LoginIdempotencyReplaySnapshot[outcomeCode=" + outcomeCode + ", opaqueCommitRef=<redacted>]";
    }
}
