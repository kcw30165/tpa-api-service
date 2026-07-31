package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp;

import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordOtpCommitStatePort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengeStatePort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import reactor.core.publisher.Mono;

public final class ForgotPasswordOtpReplayResolver {
    private static final String RESEND_AVAILABLE_AT = "forgotPasswordOtpResendAvailableAt";

    private final ForgotPasswordOtpCommitStatePort commitStates;
    private final AuthFlowSessionPort sessions;
    private final OtpChallengeStatePort challenges;
    private final Clock clock;

    public ForgotPasswordOtpReplayResolver(
            ForgotPasswordOtpCommitStatePort commitStates,
            AuthFlowSessionPort sessions,
            OtpChallengeStatePort challenges,
            Clock clock) {
        this.commitStates = Objects.requireNonNull(commitStates);
        this.sessions = Objects.requireNonNull(sessions);
        this.challenges = Objects.requireNonNull(challenges);
        this.clock = Objects.requireNonNull(clock);
    }

    public Mono<ForgotPasswordOtpReplayResult> resolve(String opaqueCommitRef) {
        if (opaqueCommitRef == null || opaqueCommitRef.isBlank()) {
            return Mono.just(systemError());
        }
        return commitStates.load(opaqueCommitRef)
                .flatMap(optional -> optional
                        .map(this::resolveCommitState)
                        .orElseGet(() -> Mono.just(systemError())))
                .onErrorReturn(systemError());
    }

    private Mono<ForgotPasswordOtpReplayResult> resolveCommitState(ForgotPasswordOtpCommitState commitState) {
        Instant now = Instant.now(clock);
        return sessions.load(new AuthFlowSessionId(commitState.authFlowSessionId()))
                .flatMap(optional -> optional
                        .map(session -> validateSession(commitState, session, now))
                        .orElseGet(() -> Mono.just(systemError())));
    }

    private Mono<ForgotPasswordOtpReplayResult> validateSession(
            ForgotPasswordOtpCommitState commitState,
            AuthFlowSession session,
            Instant now) {
        if (session.scenario() != AuthFlowScenario.FORGOT_PASSWORD
                || session.status() != AuthFlowStatus.STARTED
                || !commitState.otpChallengeId().equals(session.activeOtpChallengeId())) {
            return Mono.just(systemError());
        }
        return challenges.findById(commitState.otpChallengeId())
                .map(optional -> optional
                        .map(challenge -> validateChallenge(commitState, session, challenge, now))
                        .orElseGet(this::systemError));
    }

    private ForgotPasswordOtpReplayResult validateChallenge(
            ForgotPasswordOtpCommitState commitState,
            AuthFlowSession session,
            OtpChallenge challenge,
            Instant now) {
        if (challenge.scenario() != OtpScenario.FORGOT_PASSWORD
                || !commitState.authFlowSessionId().equals(challenge.workflowRef())
                || !commitState.otpChallengeId().equals(challenge.otpChallengeId())
                || !commitState.challengeExpiresAt().equals(challenge.expiresAt())) {
            return systemError();
        }
        if (!now.isBefore(commitState.challengeExpiresAt())) {
            return new ForgotPasswordOtpReplayResult(
                    ForgotPasswordOtpReplayResult.Status.EXPIRED,
                    session.data().maskedDestination(),
                    0,
                    0);
        }
        String resendValue = session.data().attributes().get(RESEND_AVAILABLE_AT);
        if (resendValue == null || resendValue.isBlank()) {
            return systemError();
        }
        try {
            Instant resendAvailableAt = Instant.parse(resendValue);
            int expiresIn = secondsBetween(now, challenge.expiresAt());
            int resendIn = secondsBetween(now, resendAvailableAt);
            return new ForgotPasswordOtpReplayResult(
                    ForgotPasswordOtpReplayResult.Status.ACTIVE,
                    session.data().maskedDestination(),
                    expiresIn,
                    resendIn);
        } catch (RuntimeException malformedTiming) {
            return systemError();
        }
    }

    private int secondsBetween(Instant now, Instant end) {
        long seconds = Math.max(0L, Duration.between(now, end).toSeconds());
        return Math.toIntExact(Math.min(Integer.MAX_VALUE, seconds));
    }

    private ForgotPasswordOtpReplayResult systemError() {
        return new ForgotPasswordOtpReplayResult(
                ForgotPasswordOtpReplayResult.Status.SYSTEM_ERROR,
                null,
                null,
                null);
    }
}
