package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

import java.util.Objects;

import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;

/**
 * Application result of the Username Recovery request flow.
 *
 * <p>Design references: {@code docs/design/6-username-recovery/username-recovery-api-contract-design.md}
 * section 5 (successful request returns the session status, the opaque Scheme-Ref, and a masked
 * effective destination) and section 8 (UR-REQ-004..011 outcomes) and
 * {@code docs/design/6-username-recovery/username-recovery-solution-design.md} section 9
 * (sender {@code ACCEPTED} maps the candidate to {@code REQUESTED}; {@code UNKNOWN_OUTCOME} is
 * never blindly resent).
 *
 * <p>Invariants: {@code outcome} is required. {@code REQUEST_ACCEPTED} carries the resulting
 * {@link UsernameRecoveryWorkflowStatus} ({@code PARTIALLY_REQUESTED} or {@code COMPLETED}), a
 * session-bound {@code schemeRef}, a nonblank masked destination, and a destination type.
 * All other outcomes carry no success data. {@link #toString()} never discloses the opaque
 * Scheme-Ref; the masked destination is already masked for FE display.
 */
public record UsernameRecoveryRequestResult(
        Outcome outcome,
        UsernameRecoveryWorkflowStatus workflowStatus,
        UsernameRecoverySchemeRef schemeRef,
        String maskedDestination,
        UsernameRecoveryDestinationType destinationType) {

    public enum Outcome {
        REQUEST_ACCEPTED,
        ALREADY_REQUESTED,
        WORKFLOW_COMPLETED,
        UNREGISTERED_CANDIDATE,
        INVALID_SCHEME_REF,
        SYSTEM_FAILED,
        UNKNOWN_OUTCOME
    }

    public UsernameRecoveryRequestResult {
        Objects.requireNonNull(outcome, "outcome must not be null");
        if (outcome == Outcome.REQUEST_ACCEPTED) {
            if (workflowStatus == null) {
                throw new IllegalArgumentException("accepted request result requires a workflowStatus");
            }
            if (workflowStatus != UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED
                    && workflowStatus != UsernameRecoveryWorkflowStatus.COMPLETED) {
                throw new IllegalArgumentException(
                        "accepted request workflowStatus must be PARTIALLY_REQUESTED or COMPLETED");
            }
            if (schemeRef == null) {
                throw new IllegalArgumentException("accepted request result requires a schemeRef");
            }
            if (maskedDestination == null || maskedDestination.isBlank()) {
                throw new IllegalArgumentException("accepted request result maskedDestination must not be blank");
            }
            if (destinationType == null) {
                throw new IllegalArgumentException("accepted request result requires a destinationType");
            }
        } else if (workflowStatus != null || schemeRef != null
                || maskedDestination != null || destinationType != null) {
            throw new IllegalArgumentException("non-accepted request result must not carry success data");
        }
    }

    @Override
    public String toString() {
        return "UsernameRecoveryRequestResult[outcome=" + outcome
                + ", workflowStatus=" + workflowStatus
                + ", schemeRef=" + (schemeRef == null ? null : "<redacted>")
                + ", maskedDestination=" + maskedDestination
                + ", destinationType=" + destinationType + "]";
    }
}
