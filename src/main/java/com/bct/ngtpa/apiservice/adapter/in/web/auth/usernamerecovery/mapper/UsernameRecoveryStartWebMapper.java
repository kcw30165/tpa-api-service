package com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.dto.UsernameRecoverySchemeResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.dto.UsernameRecoveryStartDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.dto.UsernameRecoveryStartRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.dto.UsernameRecoveryWorkflowResultResponse;
import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoverySchemeProjection;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Stateless web mapper for Username Recovery {@code /start}.
 *
 * <p>Owns the public identity mapping ({@code STAFF_NO ->} {@link MemberIdentityType#STAFFNO},
 * {@code GPN}/{@code GPFS}/{@code ID} direct) with fail-closed rejection of unknown, blank,
 * case-mutated, or whitespace-padded values, and the approved Start next-step/data projection.
 *
 * <p>Header/context values (language, idempotencyKey) are explicit method parameters. This mapper
 * never chooses HTTP status, never builds {@code MutationResponse}, never reads headers/cookies,
 * and never catches application exceptions (Tasks 023/024 own those concerns).
 */
public final class UsernameRecoveryStartWebMapper {

  public UsernameRecoveryStartCommand toCommand(
      UsernameRecoveryStartRequest request, Locale language, String idempotencyKey) {
    Objects.requireNonNull(request, "username recovery start request required");
    Objects.requireNonNull(language, "username recovery start language required");
    Objects.requireNonNull(idempotencyKey, "username recovery start idempotencyKey required");
    return new UsernameRecoveryStartCommand(
        mapIdentityType(request.identityType()),
        request.identityValue(),
        request.dateOfBirth(),
        request.email(),
        request.mobile(),
        request.captchaChallengeId(),
        language,
        idempotencyKey);
  }

  public UsernameRecoveryWorkflowResultResponse<UsernameRecoveryStartDataResponse> toResult(
      UsernameRecoveryStartResult result) {
    Objects.requireNonNull(result, "username recovery start result required");
    return switch (result.outcome()) {
      case SESSION_CREATED ->
          new UsernameRecoveryWorkflowResultResponse<>(
              WorkflowNextStep.SELECT_ACCOUNT,
              new UsernameRecoveryStartDataResponse(
                  result.csrfToken(),
                  result.expiresInSeconds(),
                  toSchemeResponses(result.schemes())));
      case MEMBER_NOT_FOUND ->
          new UsernameRecoveryWorkflowResultResponse<>(WorkflowNextStep.RESTART_FLOW, null);
    };
  }

  private static List<UsernameRecoverySchemeResponse> toSchemeResponses(
      List<UsernameRecoverySchemeProjection> projections) {
    return projections.stream().map(UsernameRecoveryStartWebMapper::toSchemeResponse).toList();
  }

  private static UsernameRecoverySchemeResponse toSchemeResponse(
      UsernameRecoverySchemeProjection projection) {
    String schemeRef = projection.schemeRef() == null ? null : projection.schemeRef().value();
    return new UsernameRecoverySchemeResponse(
        schemeRef, projection.accountEnv(), projection.trustCode(), projection.registered());
  }

  private static MemberIdentityType mapIdentityType(String publicValue) {
    if (publicValue == null || publicValue.isBlank()) {
      throw new IllegalArgumentException("public identity type must not be null or blank");
    }
    return switch (publicValue) {
      case "GPN" -> MemberIdentityType.GPN;
      case "GPFS" -> MemberIdentityType.GPFS;
      case "STAFF_NO" -> MemberIdentityType.STAFFNO;
      case "ID" -> MemberIdentityType.ID;
      default -> throw new IllegalArgumentException("unsupported public identity type");
    };
  }
}
