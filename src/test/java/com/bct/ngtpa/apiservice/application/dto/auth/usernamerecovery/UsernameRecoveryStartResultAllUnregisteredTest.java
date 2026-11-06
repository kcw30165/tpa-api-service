package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class UsernameRecoveryStartResultAllUnregisteredTest {

  private static UsernameRecoverySchemeProjection unregistered(String ref, String trustCode) {
    return new UsernameRecoverySchemeProjection(
        new UsernameRecoverySchemeRef(ref), "DB", trustCode, false);
  }

  @Test
  void sessionCreatedAcceptsAnAllUnregisteredSessionBoundSchemeList() {
    var result =
        new UsernameRecoveryStartResult(
            UsernameRecoveryStartResult.Outcome.SESSION_CREATED,
            new AuthFlowSessionId("afs_opaque-1"),
            "csrf-value",
            600,
            List.of(unregistered("sch_1", "DBA"), unregistered("sch_2", "DBB")));

    assertThat(result.outcome()).isEqualTo(UsernameRecoveryStartResult.Outcome.SESSION_CREATED);
    assertThat(result.schemes())
        .hasSize(2)
        .allSatisfy(
            scheme -> {
              assertThat(scheme.registered()).isFalse();
              assertThat(scheme.schemeRef()).isNotNull();
            });
  }

  @Test
  void sessionCreatedStillRequiresASchemeRefOnEveryProjection() {
    var withoutSchemeRef = List.of(new UsernameRecoverySchemeProjection(null, "DB", "DBA", false));

    assertThatThrownBy(
            () ->
                new UsernameRecoveryStartResult(
                    UsernameRecoveryStartResult.Outcome.SESSION_CREATED,
                    null,
                    "csrf",
                    600,
                    withoutSchemeRef))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("session-created result requires a schemeRef for every projection");
  }

  @Test
  void theStartOutcomeModelHasNoZeroRegisteredCase() {
    assertThat(
            Arrays.stream(UsernameRecoveryStartResult.Outcome.values())
                .map(UsernameRecoveryStartResult.Outcome::name))
        .containsExactlyInAnyOrder("SESSION_CREATED", "MEMBER_NOT_FOUND");
  }
}
