package com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword;

record RedisForgotPasswordIdempotencyRecord(
        String action,
        String idempotencyKey,
        String requestHash,
        String status,
        RedisForgotPasswordIdempotencyReplaySnapshotRecord replaySnapshot,
        String createdAt,
        String updatedAt,
        String expiresAt,
        String pendingOpaqueCommitRef) {
    RedisForgotPasswordIdempotencyRecord(
            String action,
            String idempotencyKey,
            String requestHash,
            String status,
            RedisForgotPasswordIdempotencyReplaySnapshotRecord replaySnapshot,
            String createdAt,
            String updatedAt,
            String expiresAt) {
        this(action, idempotencyKey, requestHash, status, replaySnapshot,
                createdAt, updatedAt, expiresAt, null);
    }
}