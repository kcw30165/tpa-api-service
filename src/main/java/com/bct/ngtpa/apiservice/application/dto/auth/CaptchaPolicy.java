package com.bct.ngtpa.apiservice.application.dto.auth;

public record CaptchaPolicy(
                int length,
                String alphabet,
                boolean caseSensitive,
                int ttlSeconds,
                int maxAttempts) {
}