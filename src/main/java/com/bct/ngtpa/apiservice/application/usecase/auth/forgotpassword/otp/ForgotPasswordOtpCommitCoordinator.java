package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import java.time.*;
import java.util.Map;
import reactor.core.publisher.Mono;

public final class ForgotPasswordOtpCommitCoordinator {
    private final ForgotPasswordIdempotencyPort idem;
    private final ForgotPasswordOtpCommitStatePort commits;
    private final ForgotPasswordOtpCommitRetentionPolicy retention;

    public ForgotPasswordOtpCommitCoordinator(ForgotPasswordIdempotencyPort i, ForgotPasswordOtpCommitStatePort c,
            ForgotPasswordOtpCommitRetentionPolicy r) {
        idem = i;
        commits = c;
        retention = r;
    }

    public Mono<ForgotPasswordIdempotencyRecord> complete(ForgotPasswordIdempotencyRecord processing,
            ForgotPasswordOtpCommitState state, Instant now) {
        if (processing.status() != ForgotPasswordIdempotencyStatus.PROCESSING
                || processing.pendingOpaqueCommitRef() == null)
            return Mono.error(new IllegalStateException("pending commit reference required"));
        String ref = processing.pendingOpaqueCommitRef();
        Duration ttl = retention.ttl(now, state.challengeExpiresAt(), processing.expiresAt());
        return commits.saveIfAbsent(ref, state, ttl).flatMap(saved -> commits.load(ref)).flatMap(found -> {
            if (found.isEmpty() || !found.get().equals(state))
                return Mono.error(new IllegalStateException("commit state conflict"));
            Duration remaining = Duration.between(now, processing.expiresAt());
            return idem.markCompleted(new ForgotPasswordIdempotencyCompleteCommand(processing.action(),
                    processing.idempotencyKey(), processing.requestHash(),
                    new ForgotPasswordIdempotencyReplaySnapshot("OTP_ISSUED", ref, Map.of()), now, remaining));
        });
    }
}
