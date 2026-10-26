package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class UsernameRecoveryRequestCommandTest {

  private static final String IDEMPOTENCY_KEY = "idem-request-123";

  @Test
  void requiresSessionSchemeRefAndIdempotencyKey() {
    var schemeRef = new UsernameRecoverySchemeRef("sch_1");
    assertThatThrownBy(
            () ->
                new UsernameRecoveryRequestCommand(
                    null, schemeRef, IDEMPOTENCY_KEY, null))
        .isInstanceOf(NullPointerException.class);

    var authFlowSessionId = new AuthFlowSessionId("afs_1");
    assertThatThrownBy(
            () ->
                new UsernameRecoveryRequestCommand(
                    authFlowSessionId, null, IDEMPOTENCY_KEY, null))
        .isInstanceOf(NullPointerException.class);

    var authFlowSessionIdForNullKey = new AuthFlowSessionId("afs_1");
    var schemeRefForNullKey = new UsernameRecoverySchemeRef("sch_1");
    assertThatThrownBy(
            () ->
                new UsernameRecoveryRequestCommand(
                    authFlowSessionIdForNullKey,
                    schemeRefForNullKey,
                    null,
                    null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejectsBlankIdempotencyKey() {
    var authFlowSessionId = new AuthFlowSessionId("afs_1");
    var schemeRef = new UsernameRecoverySchemeRef("sch_1");
    assertThatThrownBy(
            () ->
                new UsernameRecoveryRequestCommand(
                    authFlowSessionId,
                    schemeRef,
                    "   ",
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("username recovery request command idempotencyKey must not be blank");
  }

  @Test
  void trimsIdempotencyKeyWithoutChangingCase() {
    var command =
        new UsernameRecoveryRequestCommand(
            new AuthFlowSessionId("afs_opaque"),
            new UsernameRecoverySchemeRef("sch_opaque-1"),
            "  IdEm_ReQuEsT_123  ",
            null);

    assertThat(command.authFlowSessionId().value()).isEqualTo("afs_opaque");
    assertThat(command.schemeRef().value()).isEqualTo("sch_opaque-1");
    assertThat(command.idempotencyKey()).isEqualTo("IdEm_ReQuEsT_123");
  }

  @Test
  void retainsSessionSchemeRefAndIdempotencyKey() {
    var command =
        new UsernameRecoveryRequestCommand(
            new AuthFlowSessionId("afs_opaque"),
            new UsernameRecoverySchemeRef("sch_opaque-1"),
            IDEMPOTENCY_KEY,
            null);

    assertThat(command.authFlowSessionId().value()).isEqualTo("afs_opaque");
    assertThat(command.schemeRef().value()).isEqualTo("sch_opaque-1");
    assertThat(command.idempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
  }

  @Test
  void toStringDoesNotDiscloseSessionSchemeRefOrIdempotencyKey() {
    var command =
        new UsernameRecoveryRequestCommand(
            new AuthFlowSessionId("afs_secret"),
            new UsernameRecoverySchemeRef("sch_secret"),
            "idem_secret_key",
            null);

    assertThat(command.toString())
        .doesNotContain("afs_secret", "sch_secret", "idem_secret_key")
        .contains(
            "authFlowSessionId=<redacted>", "schemeRef=<redacted>", "idempotencyKey=<redacted>");
  }

  @Test
  void aRequestWithoutALanguageRendersInTheLanguageThatStartStored() {
    UsernameRecoveryRequestCommand command =
        new UsernameRecoveryRequestCommand(
            new AuthFlowSessionId("afs_opaque"),
            new UsernameRecoverySchemeRef("sch_opaque-1"),
            "  IdEm_ReQuEsT_123  ",
            null);

    assertThat(command.language()).isNull();
    assertThat(command.languageOr(Locale.forLanguageTag("zh-HK")))
        .isEqualTo(Locale.forLanguageTag("zh-HK"));
  }

  @Test
  void aRequestWithALanguageRendersInThatLanguageEvenWhenStartStoredAnother() {
    UsernameRecoveryRequestCommand command =
        new UsernameRecoveryRequestCommand(
            new AuthFlowSessionId("afs_opaque"),
            new UsernameRecoverySchemeRef("sch_opaque-1"),
            "  IdEm_ReQuEsT_123  ",
            Locale.ENGLISH);

    assertThat(command.language()).isEqualTo(Locale.ENGLISH);
    assertThat(command.languageOr(Locale.forLanguageTag("zh-HK"))).isEqualTo(Locale.ENGLISH);
  }
}
