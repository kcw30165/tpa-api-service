package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

record RedisRegistrationIdempotencyRecord(
        String action,
        String idempotencyKey,
        String requestHash,
        String status,
        RedisRegistrationIdempotencyResponseSnapshotRecord responseSnapshot,
        String createdAt,
        String updatedAt,
        String expiresAt) {
}
