package com.bct.ngtpa.apiservice.application.dto.session.authenticated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuthenticatedSessionModelTest {
  private static final Instant NOW = Instant.parse("2026-07-20T00:00:00Z");

  @Test
  void memberSessionCarriesActorAndOrderedAccountMappings() {
    var account =
        new AuthenticatedSessionAccount(
            new AuthenticatedAccountRef("acc_abcdefghijklmnopqrstuvwx"),
            "DB",
            "00000008802",
            "0001",
            "DBA",
            "OE",
            "T",
            LocalDate.of(2026, 7, 20));
    var s =
        new AuthenticatedSession(
            new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx"),
            new ActorContext("member", ActorUserRole.ROLE_USER_MBR),
            List.of(account),
            AuthenticatedSessionStatus.ACTIVE,
            "csrf_hash",
            NOW,
            NOW,
            NOW.plusSeconds(1800),
            NOW.plusSeconds(28800));
    assertThat(s.accounts()).containsExactly(account);
    assertThat(s.findAccount(account.accountRef())).contains(account);
    assertThat(s.isUsable(NOW.plusSeconds(10))).isTrue();
  }

  @Test
  void actorTypeMustNotExist() {
    assertThat(
            java.util.Arrays.stream(ActorContext.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
        .containsExactly("actorUserId", "actorUserRole");
  }

  @Test
  void expiredOrRevokedSessionIsNotUsable() {
    var base = session(AuthenticatedSessionStatus.ACTIVE, NOW.plusSeconds(5));
    assertThat(base.isUsable(NOW.plusSeconds(6))).isFalse();
    assertThat(
            session(AuthenticatedSessionStatus.REVOKED, NOW.plusSeconds(50))
                .isUsable(NOW.plusSeconds(1)))
        .isFalse();
  }

  @Test
  void identifiersAndToStringAreSafe() {
    assertThatThrownBy(() -> new AuthenticatedSessionId("sid_short"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(session(AuthenticatedSessionStatus.ACTIVE, NOW.plusSeconds(50)).toString())
        .doesNotContain(
            "sid_abcdefghijklmnopqrstuvwx", "member", "00000008802", "0001", "csrf_hash");
  }

  private AuthenticatedSession session(AuthenticatedSessionStatus status, Instant expires) {
    return new AuthenticatedSession(
        new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx"),
        new ActorContext("member", ActorUserRole.ROLE_USER_MBR),
        List.of(
            new AuthenticatedSessionAccount(
                new AuthenticatedAccountRef("acc_abcdefghijklmnopqrstuvwx"),
                "DB",
                "00000008802",
                "0001",
                "DBA",
                null,
                null,
                null)),
        status,
        "csrf_hash",
        NOW,
        NOW,
        expires,
        NOW.plusSeconds(100));
  }
}
