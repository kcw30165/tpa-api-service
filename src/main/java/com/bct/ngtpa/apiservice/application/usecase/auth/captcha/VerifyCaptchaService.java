package com.bct.ngtpa.apiservice.application.usecase.auth.captcha;

import com.bct.ngtpa.apiservice.application.dto.auth.CaptchaPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaVerificationMutationResult;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaVerificationOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.VerifyCaptchaChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.VerifyCaptchaChallengeResult;
import com.bct.ngtpa.apiservice.application.port.in.VerifyCaptchaUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaAnswerHasherPort;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaChallengePort;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import reactor.core.publisher.Mono;

public class VerifyCaptchaService implements VerifyCaptchaUseCase {
    private final AuthWorkflowPolicyPort authWorkflowPolicyPort;
    private final CaptchaAnswerHasherPort captchaAnswerHasherPort;
    private final CaptchaChallengePort captchaChallengePort;
    private final Clock clock;

    public VerifyCaptchaService(
            AuthWorkflowPolicyPort authWorkflowPolicyPort,
            CaptchaAnswerHasherPort captchaAnswerHasherPort,
            CaptchaChallengePort captchaChallengePort,
            Clock clock) {
        this.authWorkflowPolicyPort = Objects.requireNonNull(authWorkflowPolicyPort,
                "authWorkflowPolicyPort must not be null");
        this.captchaAnswerHasherPort = Objects.requireNonNull(captchaAnswerHasherPort,
                "captchaAnswerHasherPort must not be null");
        this.captchaChallengePort = Objects.requireNonNull(captchaChallengePort,
                "captchaChallengePort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public Mono<VerifyCaptchaChallengeResult> verify(VerifyCaptchaChallengeCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        CaptchaPolicy policy = captchaPolicy();
        Instant now = clock.instant();
        return captchaChallengePort.load(command.captchaChallengeId())
                .flatMap(optionalChallenge -> optionalChallenge
                        .map(challenge -> verifyExistingChallenge(command, challenge, policy, now))
                        .orElseGet(() -> Mono.just(result(
                                CaptchaVerificationOutcome.NOT_FOUND, false, null, 0))));
    }

    private Mono<VerifyCaptchaChallengeResult> verifyExistingChallenge(
            VerifyCaptchaChallengeCommand command,
            CaptchaChallenge challenge,
            CaptchaPolicy policy,
            Instant now) {
        return switch (challenge.status()) {
            case VERIFIED -> Mono.just(result(CaptchaVerificationOutcome.INVALID, false, null, 0));
            case LOCKED -> Mono.just(result(CaptchaVerificationOutcome.LOCKED, false, null, 0));
            case CONSUMED -> Mono.just(result(CaptchaVerificationOutcome.CONSUMED, false, null, 0));
            case REFRESHED -> Mono.just(result(CaptchaVerificationOutcome.REFRESHED, false, null, 0));
            case EXPIRED -> Mono.just(result(CaptchaVerificationOutcome.EXPIRED, false, null, 0));
            case CREATED -> verifyCreatedChallenge(command, challenge, policy, now);
        };
    }

    private Mono<VerifyCaptchaChallengeResult> verifyCreatedChallenge(
            VerifyCaptchaChallengeCommand command,
            CaptchaChallenge challenge,
            CaptchaPolicy policy,
            Instant now) {
        if (!challenge.expiresAt().isAfter(now)) {
            return Mono.just(result(CaptchaVerificationOutcome.EXPIRED, false, null, 0));
        }
        boolean answerMatched = captchaAnswerHasherPort.matches(
                command.answer(), challenge.answerHash(), policy.caseSensitive());
        return captchaChallengePort.applyVerificationAttempt(challenge, answerMatched, now)
                .flatMap(mutation -> mapMutation(command, challenge.captchaChallengeId(), policy, now, mutation));
    }

    private Mono<VerifyCaptchaChallengeResult> mapMutation(
            VerifyCaptchaChallengeCommand command,
            CaptchaChallengeId captchaChallengeId,
            CaptchaPolicy policy,
            Instant now,
            CaptchaVerificationMutationResult mutation) {
        return switch (mutation.outcome()) {
            case VERIFIED -> Mono.just(result(
                    CaptchaVerificationOutcome.VERIFIED,
                    true,
                    mutation.challenge().captchaChallengeId(),
                    remainingAttempts(mutation.challenge())));
            case INVALID -> Mono.just(result(
                    CaptchaVerificationOutcome.INVALID,
                    false,
                    null,
                    remainingAttempts(mutation.challenge())));
            case LOCKED -> Mono.just(result(CaptchaVerificationOutcome.LOCKED, false, null, 0));
            case NOT_FOUND -> Mono.just(result(CaptchaVerificationOutcome.NOT_FOUND, false, null, 0));
            case STATE_CHANGED -> reloadAfterConflict(command, captchaChallengeId, policy, now);
        };
    }


    private Mono<VerifyCaptchaChallengeResult> reloadAfterConflict(
            VerifyCaptchaChallengeCommand command,
            CaptchaChallengeId captchaChallengeId,
            CaptchaPolicy policy,
            Instant now) {
        return captchaChallengePort.load(captchaChallengeId)
                .map(current -> current
                        .map(this::classifyConflictWinner)
                        .orElseGet(() -> result(
                                CaptchaVerificationOutcome.NOT_FOUND, false, null, 0)));
    }

    private VerifyCaptchaChallengeResult classifyConflictWinner(CaptchaChallenge challenge) {
        return switch (challenge.status()) {
            case CREATED, VERIFIED -> result(CaptchaVerificationOutcome.INVALID, false, null, 0);
            case LOCKED -> result(CaptchaVerificationOutcome.LOCKED, false, null, 0);
            case CONSUMED -> result(CaptchaVerificationOutcome.CONSUMED, false, null, 0);
            case REFRESHED -> result(CaptchaVerificationOutcome.REFRESHED, false, null, 0);
            case EXPIRED -> result(CaptchaVerificationOutcome.EXPIRED, false, null, 0);
        };
    }

    private CaptchaPolicy captchaPolicy() {
        return Objects.requireNonNull(
                authWorkflowPolicyPort.registrationPolicy().captcha(),
                "registration captcha policy must not be null");
    }

    private int remainingAttempts(CaptchaChallenge challenge) {
        return Math.max(0, challenge.maxAttempts() - challenge.failedAttemptCount());
    }

    private VerifyCaptchaChallengeResult result(
            CaptchaVerificationOutcome outcome,
            boolean verified,
            CaptchaChallengeId captchaChallengeId,
            int remainingAttempts) {
        return new VerifyCaptchaChallengeResult(outcome, verified, captchaChallengeId, remainingAttempts);
    }
}
