package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.context;

import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryDeliveryState;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryVerifiedDestination;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Feature-owned protected state embedded in one revisioned auth-flow envelope. */
public record UsernameRecoveryAuthFlowContext(
    UsernameRecoveryWorkflowStatus workflowStatus,
    UsernameRecoveryVerifiedDestination verifiedDestination,
    Locale language,
    List<UsernameRecoverySessionCandidate> candidates) {

  private record EnvTrustKey(String accountEnv, String trustCode) {}

  public UsernameRecoveryAuthFlowContext {
    Objects.requireNonNull(workflowStatus, "workflowStatus must not be null");
    Objects.requireNonNull(verifiedDestination, "verifiedDestination must not be null");
    Objects.requireNonNull(language, "language must not be null");
    candidates = candidates == null ? List.of() : List.copyOf(candidates);
    if (candidates.isEmpty()
        || candidates.stream().noneMatch(UsernameRecoverySessionCandidate::registered)) {
      throw new IllegalArgumentException("context requires at least one registered candidate");
    }
    if (candidates.stream().map(UsernameRecoverySessionCandidate::schemeRef).distinct().count()
        != candidates.size()) {
      throw new IllegalArgumentException("duplicate schemeRef is rejected");
    }
    if (candidates.stream()
            .map(c -> new EnvTrustKey(c.accountEnv(), c.trustCode()))
            .distinct()
            .count()
        != candidates.size()) {
      throw new IllegalArgumentException("duplicate accountEnv+trustCode candidate is rejected");
    }
    boolean allRequested =
        candidates.stream()
            .filter(UsernameRecoverySessionCandidate::registered)
            .allMatch(c -> c.deliveryState() == UsernameRecoveryDeliveryState.REQUESTED);
    if (allRequested != (workflowStatus == UsernameRecoveryWorkflowStatus.COMPLETED)) {
      throw new IllegalArgumentException("candidate state and workflow completion must agree");
    }
  }

  public UsernameRecoveryAuthFlowContext reserve(UsernameRecoverySchemeRef schemeRef) {
    return mutate(
        schemeRef,
        UsernameRecoveryDeliveryState.AVAILABLE,
        UsernameRecoveryDeliveryState.RESERVED,
        null,
        null,
        workflowStatus);
  }

  public UsernameRecoveryAuthFlowContext markRequested(
      UsernameRecoverySchemeRef schemeRef, String senderReference, Instant requestedAt) {
    Objects.requireNonNull(senderReference, "senderReference must not be null");
    Objects.requireNonNull(requestedAt, "requestedAt must not be null");
    int index = indexOf(schemeRef);
    requireOpen();
    UsernameRecoverySessionCandidate current = candidates.get(index);
    requireRegistered(current);
    if (current.deliveryState() != UsernameRecoveryDeliveryState.RESERVED) {
      throw new IllegalStateException("candidate must be reserved before requesting");
    }
    List<UsernameRecoverySessionCandidate> next =
        replace(
            index,
            current.withState(
                UsernameRecoveryDeliveryState.REQUESTED, requestedAt, senderReference));
    boolean complete =
        next.stream()
            .filter(UsernameRecoverySessionCandidate::registered)
            .allMatch(c -> c.deliveryState() == UsernameRecoveryDeliveryState.REQUESTED);
    return new UsernameRecoveryAuthFlowContext(
        complete
            ? UsernameRecoveryWorkflowStatus.COMPLETED
            : UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED,
        verifiedDestination,
        language,
        next);
  }

  public UsernameRecoveryAuthFlowContext markFailed(UsernameRecoverySchemeRef schemeRef) {
    return mutate(
        schemeRef,
        UsernameRecoveryDeliveryState.RESERVED,
        UsernameRecoveryDeliveryState.FAILED,
        null,
        null,
        workflowStatus);
  }

  public UsernameRecoveryAuthFlowContext markUnknownOutcome(UsernameRecoverySchemeRef schemeRef) {
    return mutate(
        schemeRef,
        UsernameRecoveryDeliveryState.RESERVED,
        UsernameRecoveryDeliveryState.UNKNOWN_OUTCOME,
        null,
        null,
        workflowStatus);
  }

  private UsernameRecoveryAuthFlowContext mutate(
      UsernameRecoverySchemeRef ref,
      UsernameRecoveryDeliveryState expected,
      UsernameRecoveryDeliveryState nextState,
      Instant requestedAt,
      String senderReference,
      UsernameRecoveryWorkflowStatus nextStatus) {
    int index = indexOf(ref);
    requireOpen();
    UsernameRecoverySessionCandidate current = candidates.get(index);
    requireRegistered(current);
    if (current.deliveryState() != expected) {
      throw new IllegalStateException(
          "candidate delivery state is not eligible: " + current.deliveryState());
    }
    return new UsernameRecoveryAuthFlowContext(
        nextStatus,
        verifiedDestination,
        language,
        replace(index, current.withState(nextState, requestedAt, senderReference)));
  }

  private void requireOpen() {
    if (workflowStatus != UsernameRecoveryWorkflowStatus.ACTIVE
        && workflowStatus != UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED) {
      throw new IllegalStateException("workflow context is not open: " + workflowStatus);
    }
  }

  private static void requireRegistered(UsernameRecoverySessionCandidate candidate) {
    if (!candidate.registered()) {
      throw new IllegalStateException("unregistered candidate cannot participate in delivery");
    }
  }

  private int indexOf(UsernameRecoverySchemeRef ref) {
    Objects.requireNonNull(ref, "schemeRef must not be null");
    for (int i = 0; i < candidates.size(); i++) {
      if (candidates.get(i).schemeRef().equals(ref)) return i;
    }
    throw new IllegalArgumentException("unknown schemeRef");
  }

  private List<UsernameRecoverySessionCandidate> replace(
      int index, UsernameRecoverySessionCandidate value) {
    List<UsernameRecoverySessionCandidate> next = new ArrayList<>(candidates);
    next.set(index, value);
    return List.copyOf(next);
  }

  @Override
  public String toString() {
    return "UsernameRecoveryAuthFlowContext[workflowStatus="
        + workflowStatus
        + ", verifiedDestination=<redacted>, language="
        + language
        + ", candidateCount="
        + candidates.size()
        + "]";
  }
}
