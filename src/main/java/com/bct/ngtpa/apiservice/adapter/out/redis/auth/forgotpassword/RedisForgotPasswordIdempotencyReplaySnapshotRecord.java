package com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword;

import java.util.Map;

record RedisForgotPasswordIdempotencyReplaySnapshotRecord(
        String outcomeCode,
        String opaqueCommitRef,
        Map<String, String> safeHeaders) {
}