package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChannel;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpScenario;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpStatus;
import java.time.Instant;

record RedisOtpChallengeRecord(
        String otpChallengeId,
        String scenario,
        String workflowRef,
        String channel,
        String destinationHash,
        String codeHash,
        String status,
        int failedAttemptCount,
        int maxVerifyAttempts,
        int resendAttemptCount,
        int maxResendAttempts,
        String createdAt,
        String expiresAt,
        String lastSentAt,
        String invalidatedAt,
        String verifiedAt) {

    static RedisOtpChallengeRecord from(OtpChallenge challenge) {
        return new RedisOtpChallengeRecord(
                challenge.otpChallengeId(),
                challenge.scenario().name(),
                challenge.workflowRef(),
                challenge.channel().name(),
                challenge.destinationHash(),
                challenge.codeHash(),
                challenge.status().name(),
                challenge.failedAttemptCount(),
                challenge.maxVerifyAttempts(),
                challenge.resendAttemptCount(),
                challenge.maxResendAttempts(),
                instantToString(challenge.createdAt()),
                instantToString(challenge.expiresAt()),
                instantToString(challenge.lastSentAt()),
                instantToString(challenge.invalidatedAt()),
                instantToString(challenge.verifiedAt()));
    }

    OtpChallenge toOtpChallenge() {
        return new OtpChallenge(
                otpChallengeId,
                OtpScenario.valueOf(scenario),
                workflowRef,
                OtpChannel.valueOf(channel),
                destinationHash,
                codeHash,
                OtpStatus.valueOf(status),
                failedAttemptCount,
                maxVerifyAttempts,
                resendAttemptCount,
                maxResendAttempts,
                instantFromString(createdAt),
                instantFromString(expiresAt),
                instantFromString(lastSentAt),
                instantFromString(invalidatedAt),
                instantFromString(verifiedAt));
    }

    private static String instantToString(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private static Instant instantFromString(String value) {
        return value == null ? null : Instant.parse(value);
    }
}