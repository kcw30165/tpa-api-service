package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import java.util.Map;

record RedisRegistrationIdempotencyResponseSnapshotRecord(
    String status, String serializedBody, Map<String, String> safeHeaders) {}
