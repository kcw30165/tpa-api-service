package com.bct.ngtpa.apiservice.application.usecase.session.authflow;

import com.bct.ngtpa.apiservice.application.dto.session.SessionConcurrencyPolicy;
import com.bct.ngtpa.apiservice.application.dto.session.SessionTtlPolicy;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import java.util.Objects;

public record StartAuthFlowSessionWithConcurrencyCommand(
    AuthFlowScenario scenario,
    AuthFlowStatus initialStatus,
    SessionTtlPolicy ttlPolicy,
    String sessionIdPrefix,
    int sessionIdEntropyBytes,
    AuthFlowSessionData data,
    String csrfTokenHash,
    String activeOtpChallengeId,
    AuthFlowSessionId existingAuthFlowSessionId,
    SessionConcurrencyPolicy concurrencyPolicy,
    String correlationId) {
  public StartAuthFlowSessionWithConcurrencyCommand {
    Objects.requireNonNull(scenario, "scenario must not be null");
    Objects.requireNonNull(initialStatus, "initialStatus must not be null");
    Objects.requireNonNull(ttlPolicy, "ttlPolicy must not be null");
    Objects.requireNonNull(sessionIdPrefix, "sessionIdPrefix must not be null");
    data = data == null ? AuthFlowSessionData.empty() : data;
    Objects.requireNonNull(concurrencyPolicy, "concurrencyPolicy must not be null");
  }

  @Override
  public String toString() {
    return "StartAuthFlowSessionWithConcurrencyCommand[scenario="
        + scenario
        + ", initialStatus="
        + initialStatus
        + ", ttlPolicy="
        + ttlPolicy
        + ", sessionIdPrefix="
        + sessionIdPrefix
        + ", sessionIdEntropyBytes="
        + sessionIdEntropyBytes
        + ", data="
        + data
        + ", csrfTokenHash=<redacted>"
        + ", activeOtpChallengeId=<redacted>"
        + ", existingAuthFlowSessionId=<redacted>"
        + ", concurrencyPolicy="
        + concurrencyPolicy
        + ", correlationId="
        + correlationId
        + "]";
  }
}
