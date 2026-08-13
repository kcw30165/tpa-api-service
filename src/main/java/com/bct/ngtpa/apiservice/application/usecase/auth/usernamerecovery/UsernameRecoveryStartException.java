package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import java.util.List;
import java.util.Objects;

/**
 * Typed Task 019 start rejection signal for every distinct non-result outcome of the Username
 * Recovery start flow.
 *
 * <p>Design references: {@code docs/design/6-username-recovery/username-recovery-api-contract-design.md}
 * sections 7-8 (UR-START-001..015 HTTP/status mapping) and section 9 (no silent exceptions;
 * unexpected defects reach the central exception boundary). The committed inbound port returns
 * only {@code Mono<UsernameRecoveryStartResult>}; in-progress, key-reused, unknown-outcome,
 * captcha rejection, validation failure, and definite system failure are expressed as typed
 * {@link Reason} signals the future web layer can map distinctly.
 *
 * <p>Invariants: {@code reason} is required and the message never contains raw exception text,
 * identity, DOB, contact, captcha, idempotency-key, fingerprint, session, CSRF, or destination
 * data. {@link #toString()} never discloses validation field targets or any sensitive value.
 */
public final class UsernameRecoveryStartException extends RuntimeException {

    private final Reason reason;
    private final List<UsernameRecoveryStartValidator.Error> validationErrors;

    public enum Reason {
        VALIDATION_FAILED,
        IDEMPOTENCY_IN_PROGRESS,
        IDEMPOTENCY_KEY_REUSED,
        UNKNOWN_OUTCOME_EXISTING,
        CAPTCHA_INVALID,
        CAPTCHA_NOT_FOUND,
        CAPTCHA_NOT_VERIFIED,
        CAPTCHA_EXPIRED,
        CAPTCHA_CONFLICT,
        REPLAY_REVISION_CONFLICT,
        SYSTEM_FAILURE
    }

    private UsernameRecoveryStartException(
            Reason reason, List<UsernameRecoveryStartValidator.Error> validationErrors, Throwable cause) {
        super("Username recovery start rejected: " + reason.name(), cause);
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
        this.validationErrors = validationErrors == null
                ? List.of()
                : List.copyOf(validationErrors);
    }

    public static UsernameRecoveryStartException validationFailed(
            List<UsernameRecoveryStartValidator.Error> validationErrors) {
        return new UsernameRecoveryStartException(
                Reason.VALIDATION_FAILED,
                Objects.requireNonNull(validationErrors, "validationErrors must not be null"),
                null);
    }

    public static UsernameRecoveryStartException idempotencyInProgress() {
        return new UsernameRecoveryStartException(Reason.IDEMPOTENCY_IN_PROGRESS, List.of(), null);
    }

    public static UsernameRecoveryStartException keyReused() {
        return new UsernameRecoveryStartException(Reason.IDEMPOTENCY_KEY_REUSED, List.of(), null);
    }

    public static UsernameRecoveryStartException unknownOutcomeExisting() {
        return new UsernameRecoveryStartException(Reason.UNKNOWN_OUTCOME_EXISTING, List.of(), null);
    }

    public static UsernameRecoveryStartException captchaInvalid() {
        return new UsernameRecoveryStartException(Reason.CAPTCHA_INVALID, List.of(), null);
    }

    public static UsernameRecoveryStartException captchaNotFound() {
        return new UsernameRecoveryStartException(Reason.CAPTCHA_NOT_FOUND, List.of(), null);
    }

    public static UsernameRecoveryStartException captchaNotVerified() {
        return new UsernameRecoveryStartException(Reason.CAPTCHA_NOT_VERIFIED, List.of(), null);
    }

    public static UsernameRecoveryStartException captchaExpired() {
        return new UsernameRecoveryStartException(Reason.CAPTCHA_EXPIRED, List.of(), null);
    }

    public static UsernameRecoveryStartException captchaConflict() {
        return new UsernameRecoveryStartException(Reason.CAPTCHA_CONFLICT, List.of(), null);
    }

    public static UsernameRecoveryStartException replayRevisionConflict() {
        return new UsernameRecoveryStartException(
                Reason.REPLAY_REVISION_CONFLICT, List.of(), null);
    }

    public static UsernameRecoveryStartException systemFailure() {
        return new UsernameRecoveryStartException(Reason.SYSTEM_FAILURE, List.of(), null);
    }

    public static UsernameRecoveryStartException systemFailure(Throwable cause) {
        return new UsernameRecoveryStartException(
                Reason.SYSTEM_FAILURE, List.of(), Objects.requireNonNull(cause, "cause must not be null"));
    }

    public Reason reason() {
        return reason;
    }

    public List<UsernameRecoveryStartValidator.Error> validationErrors() {
        return validationErrors;
    }

    @Override
    public String toString() {
        return "UsernameRecoveryStartException[reason=" + reason
                + ", validationErrorCount=" + validationErrors.size() + "]";
    }
}
