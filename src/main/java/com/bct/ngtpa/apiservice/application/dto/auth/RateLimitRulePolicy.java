package com.bct.ngtpa.apiservice.application.dto.auth;

public record RateLimitRulePolicy(boolean enabled, int maxAttempts, int windowSeconds) {}
