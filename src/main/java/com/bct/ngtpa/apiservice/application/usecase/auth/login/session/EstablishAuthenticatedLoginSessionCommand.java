package com.bct.ngtpa.apiservice.application.usecase.auth.login.session;

import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.auth.login.account.LoginSessionAccount;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.AuthenticatedLoginHandoffReference;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.LoginOtpConsumptionProof;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.LoginOtpVerificationOperationReference;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.util.List;
import java.util.Objects;

public record EstablishAuthenticatedLoginSessionCommand(
    AuthFlowSessionId authFlowSessionId,
    ActorContext actorContext,
    List<LoginSessionAccount> accounts,
    String csrfTokenHash,
    String correlationId,
    AuthenticatedLoginHandoffReference handoffReference,
    LoginOtpVerificationOperationReference operationReference,
    String reservationHash,
    LoginOtpConsumptionProof consumptionProof) {
  public EstablishAuthenticatedLoginSessionCommand {
    Objects.requireNonNull(authFlowSessionId, "authFlowSessionId must not be null");
    Objects.requireNonNull(actorContext, "actorContext must not be null");
    accounts = accounts == null ? List.of() : List.copyOf(accounts);
    if (accounts.isEmpty()) {
      throw new IllegalArgumentException("accounts must not be empty");
    }
    if (csrfTokenHash == null || csrfTokenHash.isBlank()) {
      throw new IllegalArgumentException("csrfTokenHash must not be blank");
    }
    csrfTokenHash = csrfTokenHash.trim();
    correlationId = correlationId == null || correlationId.isBlank() ? null : correlationId.trim();
    Objects.requireNonNull(handoffReference, "handoffReference must not be null");
    Objects.requireNonNull(operationReference, "operationReference must not be null");
    if (reservationHash == null || reservationHash.isBlank()) {
      throw new IllegalArgumentException("reservationHash must not be blank");
    }
    reservationHash = reservationHash.trim();
    Objects.requireNonNull(consumptionProof, "consumptionProof must not be null");
    if (!consumptionProof.handoffReference().equals(handoffReference)
        || !consumptionProof.operationReference().equals(operationReference)
        || !consumptionProof.reservationHash().equals(reservationHash)) {
      throw new IllegalArgumentException("consumption proof identity mismatch");
    }
  }

  @Override
  public String toString() {
    return "EstablishAuthenticatedLoginSessionCommand[authFlowSessionId=<redacted>, actor=<redacted>, accountCount="
        + accounts.size()
        + ", csrfTokenHash=<redacted>, correlationId="
        + correlationId
        + "]";
  }
}
