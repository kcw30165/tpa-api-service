package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import java.util.List;
import org.junit.jupiter.api.Test;

class UsernameRecoveryStartResultTest {

  private static UsernameRecoverySchemeProjection registeredProjection() {
    return new UsernameRecoverySchemeProjection(
        new UsernameRecoverySchemeRef("sch_1"), "DB", "DBA", true);
  }

  private static UsernameRecoverySchemeProjection unregisteredProjection() {
    return new UsernameRecoverySchemeProjection(
        new UsernameRecoverySchemeRef("sch_2"), "DB", "DBB", false);
  }

  @Test
  void sessionCreatedCarriesSessionDataAndSchemes() {
    var result =
        new UsernameRecoveryStartResult(
            UsernameRecoveryStartResult.Outcome.SESSION_CREATED,
            null,
            "csrf-value",
            600,
            List.of(registeredProjection(), unregisteredProjection()));

    assertThat(result.outcome()).isEqualTo(UsernameRecoveryStartResult.Outcome.SESSION_CREATED);
    assertThat(result.csrfToken()).isEqualTo("csrf-value");
    assertThat(result.expiresInSeconds()).isEqualTo(600);
    assertThat(result.schemes()).hasSize(2);
  }

  @Test
  void sessionCreatedRequiresSessionDataAndNonEmptySchemes() {
    assertThatThrownBy(
            () ->
                new UsernameRecoveryStartResult(
                    UsernameRecoveryStartResult.Outcome.SESSION_CREATED,
                    null,
                    null,
                    600,
                    List.of(registeredProjection())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("session-created result requires a nonblank csrfToken");
    assertThatThrownBy(
            () ->
                new UsernameRecoveryStartResult(
                    UsernameRecoveryStartResult.Outcome.SESSION_CREATED,
                    null,
                    "csrf",
                    0,
                    List.of(registeredProjection())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("session-created result requires a positive expiresInSeconds");
    assertThatThrownBy(
            () ->
                new UsernameRecoveryStartResult(
                    UsernameRecoveryStartResult.Outcome.SESSION_CREATED, null, "csrf", 600, List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("session-created result requires at least one scheme projection");
    assertThatThrownBy(
            () ->
                new UsernameRecoveryStartResult(
                    UsernameRecoveryStartResult.Outcome.SESSION_CREATED,
                    null,
                    "csrf",
                    600,
                    List.of(unregisteredProjection())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("session-created result requires at least one registered scheme");
  }

  @Test
  void sessionCreatedRequiresSchemeRefForEveryProjection() {
    assertThatThrownBy(
            () ->
                new UsernameRecoveryStartResult(
                    UsernameRecoveryStartResult.Outcome.SESSION_CREATED,
                    null,
                    "csrf",
                    600,
                    List.of(
                        registeredProjection(),
                        new UsernameRecoverySchemeProjection(null, "DB", "DBB", false))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("session-created result requires a schemeRef for every projection");
  }

  @Test
  void memberNotFoundCarriesNoSessionDataOrSchemes() {
    var result =
        UsernameRecoveryStartResult.withoutSession(
            UsernameRecoveryStartResult.Outcome.MEMBER_NOT_FOUND, List.of());

    assertThat(result.schemes()).isEmpty();
    assertThat(result.csrfToken()).isNull();
    assertThat(result.expiresInSeconds()).isNull();
  }

  @Test
  void memberNotFoundRejectsSchemesOrSessionData() {
    assertThatThrownBy(
            () ->
                UsernameRecoveryStartResult.withoutSession(
                    UsernameRecoveryStartResult.Outcome.MEMBER_NOT_FOUND,
                    List.of(registeredProjection())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("member-not-found result must not carry session data or schemes");
    assertThatThrownBy(
            () ->
                new UsernameRecoveryStartResult(
                    UsernameRecoveryStartResult.Outcome.MEMBER_NOT_FOUND, null, "csrf", null, List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("member-not-found result must not carry session data or schemes");
  }

  @Test
  void zeroRegisteredCarriesOnlyUnregisteredNullSchemeRefProjections() {
    var result =
        UsernameRecoveryStartResult.withoutSession(
            UsernameRecoveryStartResult.Outcome.ZERO_REGISTERED,
            List.of(new UsernameRecoverySchemeProjection(null, "DB", "DBA", false)));

    assertThat(result.schemes()).hasSize(1);
    assertThat(result.schemes().get(0).schemeRef()).isNull();
    assertThat(result.csrfToken()).isNull();
    assertThat(result.expiresInSeconds()).isNull();
  }

  @Test
  void zeroRegisteredRejectsSessionDataRegisteredSchemesAndSchemeRefs() {
    assertThatThrownBy(
            () ->
                new UsernameRecoveryStartResult(
                    UsernameRecoveryStartResult.Outcome.ZERO_REGISTERED,
                    null,
                    "csrf",
                    null,
                    List.of(unregisteredProjection())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("zero-registered result must not carry session data");
    assertThatThrownBy(
            () ->
                UsernameRecoveryStartResult.withoutSession(
                    UsernameRecoveryStartResult.Outcome.ZERO_REGISTERED,
                    List.of(registeredProjection())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("zero-registered result must not contain registered schemes");
    assertThatThrownBy(
            () ->
                UsernameRecoveryStartResult.withoutSession(
                    UsernameRecoveryStartResult.Outcome.ZERO_REGISTERED,
                    List.of(unregisteredProjection())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("zero-registered result projections must not carry schemeRef");
    assertThatThrownBy(
            () ->
                UsernameRecoveryStartResult.withoutSession(
                    UsernameRecoveryStartResult.Outcome.ZERO_REGISTERED, List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("zero-registered result requires at least one scheme projection");
  }

  @Test
  void requiresOutcomeAndCopiesSchemeList() {
    assertThatThrownBy(() -> UsernameRecoveryStartResult.withoutSession(null, List.of()))
        .isInstanceOf(NullPointerException.class);

    var result =
        UsernameRecoveryStartResult.withoutSession(
            UsernameRecoveryStartResult.Outcome.MEMBER_NOT_FOUND, null);
    assertThat(result.schemes()).isEmpty();
    assertThatThrownBy(() -> result.schemes().add(registeredProjection()))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void sessionCreatedCanCarryAuthFlowSessionId() {
    var sessionId = new AuthFlowSessionId("afs_opaque-1");
    var result =
        new UsernameRecoveryStartResult(
            UsernameRecoveryStartResult.Outcome.SESSION_CREATED,
            sessionId,
            "csrf-value",
            600,
            List.of(registeredProjection(), unregisteredProjection()));

    assertThat(result.outcome()).isEqualTo(UsernameRecoveryStartResult.Outcome.SESSION_CREATED);
    assertThat(result.authFlowSessionId()).isEqualTo(sessionId);
    assertThat(result.csrfToken()).isEqualTo("csrf-value");
    assertThat(result.expiresInSeconds()).isEqualTo(600);
    assertThat(result.schemes()).hasSize(2);
  }

  @Test
  void sessionCreatedResultMayCarryNullAuthFlowSessionId() {
    var result =
        new UsernameRecoveryStartResult(
            UsernameRecoveryStartResult.Outcome.SESSION_CREATED,
            null,
            "csrf-value",
            600,
            List.of(registeredProjection(), unregisteredProjection()));

    assertThat(result.outcome()).isEqualTo(UsernameRecoveryStartResult.Outcome.SESSION_CREATED);
    assertThat(result.authFlowSessionId()).isNull();
    assertThat(result.csrfToken()).isEqualTo("csrf-value");
  }

  @Test
  void nonSessionOutcomesRejectAuthFlowSessionId() {
    assertThatThrownBy(
            () ->
                new UsernameRecoveryStartResult(
                    UsernameRecoveryStartResult.Outcome.MEMBER_NOT_FOUND,
                    new AuthFlowSessionId("afs_opaque-1"),
                    null,
                    null,
                    List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("member-not-found result must not carry session data or schemes");
    assertThatThrownBy(
            () ->
                new UsernameRecoveryStartResult(
                    UsernameRecoveryStartResult.Outcome.ZERO_REGISTERED,
                    new AuthFlowSessionId("afs_opaque-1"),
                    null,
                    null,
                    List.of(unregisteredProjection())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("zero-registered result must not carry session data");
  }

  @Test
  void toStringDoesNotDiscloseCsrfToken() {
    var result =
        new UsernameRecoveryStartResult(
            UsernameRecoveryStartResult.Outcome.SESSION_CREATED,
            new AuthFlowSessionId("afs_session-secret"),
            "csrf-secret",
            600,
            List.of(registeredProjection()));

    assertThat(result.toString())
        .doesNotContain("csrf-secret")
        .doesNotContain("afs_session-secret")
        .contains("outcome=SESSION_CREATED", "expiresInSeconds=600", "schemeCount=1");
  }
}
