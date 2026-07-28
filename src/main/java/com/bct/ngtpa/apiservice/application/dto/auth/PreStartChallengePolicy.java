package com.bct.ngtpa.apiservice.application.dto.auth;

public record PreStartChallengePolicy(
                boolean enabled,
                PreStartChallengeType type) {
}