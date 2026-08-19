package com.bct.ngtpa.apiservice.application.dto.session;

public record SessionTtlPolicy(boolean enabled, boolean required, int ttlSeconds) {}
