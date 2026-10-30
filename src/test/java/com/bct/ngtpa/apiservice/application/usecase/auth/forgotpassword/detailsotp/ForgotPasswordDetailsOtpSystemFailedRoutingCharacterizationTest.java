package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details.ApimForgotPasswordDetailsLookupAdapter;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.ForgotPasswordResetAuthorization;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordProtectedPhase;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.ForgotPasswordDetailsStatus;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Sub-task 2b.2, Group B2 (Batch 2b), row 11: characterizes {@link
 * ForgotPasswordUnderLockDetailsVerifier#verify}'s routing when the downstream APIM lookup fails.
 *
 * <p>This test covers the real {@link ApimForgotPasswordDetailsLookupAdapter} (only its {@link
 * ApimExchangeExecutor} dependency stubbed) driven through the real {@link
 * ForgotPasswordUnderLockDetailsVerifier}, confirming the downstream failure becomes {@code
 * SYSTEM_FAILED}.
 *
 * <p>Public response routing through {@link ForgotPasswordDetailsOtpProtectedOperation} is not
 * characterized by this test. The verifier-to-protected-operation seam remains uncovered. A
 * second test driving {@code ForgotPasswordDetailsOtpProtectedOperation#locked} with a stubbed
 * verifier was attempted and abandoned after a bounded repair attempt, because its assumed {@code
 * locked(...)} signature did not match the real production method signature.
 */
class ForgotPasswordDetailsOtpSystemFailedRoutingCharacterizationTest {

  private ListAppender<ILoggingEvent> verifierLogAppender;
  private Logger verifierLogger;

  @BeforeEach
  void attachLogAppender() {
    verifierLogger = (Logger) LoggerFactory.getLogger(ForgotPasswordUnderLockDetailsVerifier.class);
    verifierLogAppender = new ListAppender<>();
    verifierLogAppender.start();
    verifierLogger.addAppender(verifierLogAppender);
  }

  @AfterEach
  void detachLogAppender() {
    verifierLogger.detachAppender(verifierLogAppender);
  }

  @Test
  void apimExecutorFailureRoutesToSystemFailedWithErrorLogNoStackTrace() {
    Instant now = Instant.parse("2026-08-04T10:15:30Z");
    SessionLockHandle lock = new SessionLockHandle(true, "lock-key", "owner-token");
    AuthFlowSessionId sessionId = new AuthFlowSessionId("afs_test_123");
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
                "reset-token", "afs_test_123", "reset-subject-id", now, now.plusSeconds(600), null),
            null,
            null,
            null,
            "v1",
            ForgotPasswordProtectedPhase.IDENTITY_VERIFIED);

    @SuppressWarnings("unchecked")
    ApimExchangeExecutor executor = mock(ApimExchangeExecutor.class);
    when(executor.postEnvelope(any()))
        .thenReturn((Mono) Mono.error(new IllegalStateException("downstream outage")));
    var adapter = new ApimForgotPasswordDetailsLookupAdapter(executor);
    var verifier = new ForgotPasswordUnderLockDetailsVerifier(adapter);

    StepVerifier.create(
            verifier.verify(
                lock, session, context, "04/07/1978", "HULLCS.QGLYBCY@UC.KJI", null, "req-123"))
        .assertNext(
            result ->
                assertThat(result.status()).isEqualTo(ForgotPasswordDetailsStatus.SYSTEM_FAILED))
        .verifyComplete();

    assertThat(verifierLogAppender.list).hasSize(1);
    ILoggingEvent event = verifierLogAppender.list.getFirst();
    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
    assertThat(event.getThrowableProxy()).isNull();
    assertThat(event.getFormattedMessage())
        .contains(
            "event=forgot-password.details-otp.failed",
            "requestId=req-123",
            "stage=lookup",
            "exceptionType=IllegalStateException")
        .doesNotContain("downstream outage");
  }
}
