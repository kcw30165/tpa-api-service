package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyAction;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import java.util.Objects;

public class RedisRegistrationIdempotencyKeyFactory {
    private final String prefix;

    public RedisRegistrationIdempotencyKeyFactory(String prefix) {
        Objects.requireNonNull(prefix, "prefix must not be null");
        if (prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        this.prefix = prefix.endsWith(":") ? prefix.substring(0, prefix.length() - 1) : prefix;
    }

    public String keyFor(RegistrationIdempotencyAction action, RegistrationIdempotencyKey idempotencyKey) {
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        return prefix + ":auth:idempotency:" + action.keyNamespace() + ":" + idempotencyKey.value();
    }
}
