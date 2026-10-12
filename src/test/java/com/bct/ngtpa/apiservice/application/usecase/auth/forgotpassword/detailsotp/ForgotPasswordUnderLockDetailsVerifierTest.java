package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.ForgotPasswordResetAuthorization;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordProtectedPhase;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordDetailsLookupPort;
import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ForgotPasswordUnderLockDetailsVerifierTest {
  // ----- log capture -------------------------------------------------------

  private ListAppender<ILoggingEvent> logAppender;
  private Logger operationLogger;

  @BeforeEach
  void attachLogAppender() {
    operationLogger =
        (Logger) LoggerFactory.getLogger(ForgotPasswordUnderLockDetailsVerifier.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    operationLogger.addAppender(logAppender);
  }

  @AfterEach
  void detachLogAppender() {
    operationLogger.detachAppender(logAppender);
  }

  private void assertLoggedFailure(String stage) {
    assertThat(logAppender.list).hasSize(1);
    ILoggingEvent event = logAppender.list.get(0);
    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
    assertThat(event.getThrowableProxy()).isNull();
    assertThat(event.getFormattedMessage())
        .contains(
            "event=forgot-password.details-otp.failed",
            "requestId=",
            "stage=" + stage,
            "exceptionType=IllegalStateException")
        .doesNotContain("boom");
  }

  @Test
  void lookupErrorYieldsAFailureVerificationAndLogsTheFailure() {
    Instant now = Instant.parse("2026-08-04T10:15:30Z");
    AuthFlowSessionId sessionId = new AuthFlowSessionId("afs_test_123");
    SessionLockHandle lock = new SessionLockHandle(true, "lock-key", "owner-token");
    ForgotPasswordAuthFlowContext context =
        new ForgotPasswordAuthFlowContext(
            "member-user",
            "tas-user-id",
            "HKID",
            true,
            "reset-subject-id",
            null,
            null,
            null,
            new ForgotPasswordResetAuthorization(
                "reset-token",
                sessionId.value(),
                "reset-subject-id",
                now,
                now.plusSeconds(600),
                null),
            null,
            null,
            null,
            "v1",
            ForgotPasswordProtectedPhase.IDENTITY_VERIFIED);
    AuthFlowSession session =
        new AuthFlowSession(
            sessionId,
            AuthFlowScenario.FORGOT_PASSWORD,
            AuthFlowStatus.IDENTITY_VERIFIED,
            "csrf-hash",
            null,
            new AuthFlowSessionData(
                null,
                null,
                Map.of(
                    "forgotPasswordContextCiphertext", "ciphertext",
                    "forgotPasswordContextKeyVersion", "v1",
                    "forgotPasswordContextAlgorithm", "AES_GCM_V1")),
            now.minusSeconds(30),
            now.minusSeconds(5),
            now.plusSeconds(300));
    ForgotPasswordDetailsLookupPort lookup = mock(ForgotPasswordDetailsLookupPort.class);
    when(lookup.verify(any())).thenReturn(Mono.error(new IllegalStateException("boom")));

    StepVerifier.create(
            new ForgotPasswordUnderLockDetailsVerifier(lookup)
                .verify(
                    lock, session, context, "04/07/1978", "HULLCS.QGLYBCY@UC.KJI", null, "req-123"))
        .expectNextCount(1)
        .verifyComplete();
    assertLoggedFailure("lookup");
  }
}
