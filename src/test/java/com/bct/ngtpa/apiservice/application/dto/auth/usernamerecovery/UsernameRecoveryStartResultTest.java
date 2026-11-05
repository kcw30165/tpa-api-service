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
    var registeredSchemesForMissingCsrf = List.of(registeredProjection());
    assertThatThrownBy(
            () ->
                new UsernameRecoveryStartResult(
                    UsernameRecoveryStartResult.Outcome.SESSION_CREATED,
                    null,
                    null,
                    600,
                    registeredSchemesForMissingCsrf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("session-created result requires a nonblank csrfToken");

    var registeredSchemesForInvalidExpiry = List.of(registeredProjection());
    assertThatThrownBy(
            () ->
                new UsernameRecoveryStartResult(
                    UsernameRecoveryStartResult.Outcome.SESSION_CREATED,
                    null,
                    "csrf",
                    0,
                    registeredSchemesForInvalidExpiry))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("session-created result requires a positive expiresInSeconds");

    List<UsernameRecoverySchemeProjection> emptySchemesForSessionCreated = List.of();
    assertThatThrownBy(
            () ->
                new UsernameRecoveryStartResult(
                    UsernameRecoveryStartResult.Outcome.SESSION_CREATED,
                    null,
                    "csrf",
                    600,
                    emptySchemesForSessionCreated))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("session-created result requires at least one scheme projection");
  }

  @Test
  void sessionCreatedRequiresSchemeRefForEveryProjection() {
    var registeredProjectionForSession = registeredProjection();
    var unregisteredProjectionWithoutSchemeRef =
        new UsernameRecoverySchemeProjection(null, "DB", "DBB", false);
    var projectionsWithMissingSchemeRef =
        List.of(registeredProjectionForSession, unregisteredProjectionWithoutSchemeRef);
    assertThatThrownBy(
            () ->
                new UsernameRecoveryStartResult(
                    UsernameRecoveryStartResult.Outcome.SESSION_CREATED,
                    null,
                    "csrf",
                    600,
                    projectionsWithMissingSchemeRef))
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
    var registeredSchemesForMemberNotFound = List.of(registeredProjection());
    assertThatThrownBy(
            () ->
                UsernameRecoveryStartResult.withoutSession(
                    UsernameRecoveryStartResult.Outcome.MEMBER_NOT_FOUND,
                    registeredSchemesForMemberNotFound))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("member-not-found result must not carry session data or schemes");

    List<UsernameRecoverySchemeProjection> emptySchemesForMemberNotFound = List.of();
    assertThatThrownBy(
            () ->
                new UsernameRecoveryStartResult(
                    UsernameRecoveryStartResult.Outcome.MEMBER_NOT_FOUND,
                    null,
                    "csrf",
                    null,
                    emptySchemesForMemberNotFound))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("member-not-found result must not carry session data or schemes");
  }

  @Test
  void requiresOutcomeAndCopiesSchemeList() {
    List<UsernameRecoverySchemeProjection> emptySchemes = List.of();
    assertThatThrownBy(() -> UsernameRecoveryStartResult.withoutSession(null, emptySchemes))
        .isInstanceOf(NullPointerException.class);

    var result =
        UsernameRecoveryStartResult.withoutSession(
            UsernameRecoveryStartResult.Outcome.MEMBER_NOT_FOUND, null);
    assertThat(result.schemes()).isEmpty();
    var schemes = result.schemes();
    var projection = registeredProjection();
    assertThatThrownBy(() -> schemes.add(projection))
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
    var memberNotFoundSessionId = new AuthFlowSessionId("afs_opaque-1");
    List<UsernameRecoverySchemeProjection> emptySchemes = List.of();
    assertThatThrownBy(
            () ->
                new UsernameRecoveryStartResult(
                    UsernameRecoveryStartResult.Outcome.MEMBER_NOT_FOUND,
                    memberNotFoundSessionId,
                    null,
                    null,
                    emptySchemes))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("member-not-found result must not carry session data or schemes");
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
