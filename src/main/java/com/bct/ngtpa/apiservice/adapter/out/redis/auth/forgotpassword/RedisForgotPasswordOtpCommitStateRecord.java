package com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword;

record RedisForgotPasswordOtpCommitStateRecord(String authFlowSessionId, String otpChallengeId, String committedAt,
        String challengeExpiresAt) {
}
