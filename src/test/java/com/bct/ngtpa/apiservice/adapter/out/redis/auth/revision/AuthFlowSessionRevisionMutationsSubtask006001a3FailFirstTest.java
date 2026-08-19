package com.bct.ngtpa.apiservice.adapter.out.redis.auth.revision;

import static org.assertj.core.api.Assertions.*;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AuthFlowSessionRevisionMutationsSubtask006001a3FailFirstTest {
  @Test
  void portExposesAllRevisionAwareMutations() {
    var names =
        Arrays.stream(
                com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort.class
                    .getMethods())
            .map(Method::getName)
            .toList();
    assertThat(names)
        .contains(
            "updateRevisioned",
            "touchRevisioned",
            "completeRevisioned",
            "cancelRevisioned",
            "deleteRevisioned");
  }

  @Test
  void outcomesAreTypedAndBounded() {
    assertThat(AuthFlowSessionMutationOutcome.values())
        .containsExactly(
            AuthFlowSessionMutationOutcome.APPLIED,
            AuthFlowSessionMutationOutcome.SESSION_NOT_FOUND,
            AuthFlowSessionMutationOutcome.SESSION_REVISION_MISSING,
            AuthFlowSessionMutationOutcome.SESSION_REVISION_CHANGED,
            AuthFlowSessionMutationOutcome.EXPECTED_STATE_CHANGED,
            AuthFlowSessionMutationOutcome.INVALID_TRANSITION,
            AuthFlowSessionMutationOutcome.CORRUPT_STATE,
            AuthFlowSessionMutationOutcome.SCHEMA_NOT_ESTABLISHED,
            AuthFlowSessionMutationOutcome.SYSTEM_FAILURE);
  }
}
