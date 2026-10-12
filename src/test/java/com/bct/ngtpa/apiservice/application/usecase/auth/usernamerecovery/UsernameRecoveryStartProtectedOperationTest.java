package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaConsumeOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.ConsumeCaptchaChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.*;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryLookupPort;
import com.bct.ngtpa.apiservice.application.service.auth.captcha.RegistrationCaptchaGateService;
import com.bct.ngtpa.apiservice.application.service.auth.usernamerecovery.context.UsernameRecoveryAuthFlowContextBinding;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenGenerator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenHasher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class UsernameRecoveryStartProtectedOperationTest {
  private static final Instant NOW = Instant.parse("2026-09-09T02:00:00Z");
  private static final int TTL = 600;
  private static final String CAPTCHA = "cap_f67f65980e944119b8b81c74d9a1834a";
  private static final String EMAIL = "member@example.com";
  private final List<String> calls = new ArrayList<>();
  private RegistrationCaptchaGateService captcha;
  private UsernameRecoveryLookupPort lookup;
  private AuthFlowSessionPort sessions;
  private UsernameRecoveryAuthFlowContextBinding contexts;
  private CsrfTokenGenerator csrfGenerator;
  private CsrfTokenHasher csrfHasher;
  private UsernameRecoveryStartProtectedOperation operation;
  private final UsernameRecoveryStartValidator validator = new UsernameRecoveryStartValidator();

  @BeforeEach
  void setUp() {
    captcha = mock(RegistrationCaptchaGateService.class);
    lookup = mock(UsernameRecoveryLookupPort.class);
    sessions = mock(AuthFlowSessionPort.class);
    contexts = mock(UsernameRecoveryAuthFlowContextBinding.class);
    csrfGenerator = mock(CsrfTokenGenerator.class);
    csrfHasher = mock(CsrfTokenHasher.class);
    when(csrfGenerator.generate()).thenReturn("csrfSecretValue");
    when(csrfHasher.hash("csrfSecretValue")).thenReturn("csrfHashValue");
    operation =
        new UsernameRecoveryStartProtectedOperation(
            captcha,
            lookup,
            sessions,
            contexts,
            csrfGenerator,
            csrfHasher,
            Clock.fixed(NOW, ZoneOffset.UTC),
            TTL);
  }

  @Test
  void malformedCaptchaStopsBeforeEveryPort() {
    var invalid = command("bad-captcha");
    StepVerifier.create(operation.execute(invalid, validator.validate(invalid)))
        .expectErrorSatisfies(
            e -> assertReason(e, UsernameRecoveryStartException.Reason.CAPTCHA_INVALID))
        .verify();
    verifyNoInteractions(captcha, lookup, sessions, contexts, csrfGenerator, csrfHasher);
  }

  @Test
  void captchaRejectionsPreserveTypedReasonsAndNeverLookup() {
    assertCaptchaReason(
        CaptchaConsumeOutcome.NOT_FOUND, UsernameRecoveryStartException.Reason.CAPTCHA_NOT_FOUND);
    assertCaptchaReason(
        CaptchaConsumeOutcome.NOT_VERIFIED,
        UsernameRecoveryStartException.Reason.CAPTCHA_NOT_VERIFIED);
    assertCaptchaReason(
        CaptchaConsumeOutcome.EXPIRED, UsernameRecoveryStartException.Reason.CAPTCHA_EXPIRED);
    assertCaptchaReason(
        CaptchaConsumeOutcome.ALREADY_CONSUMED,
        UsernameRecoveryStartException.Reason.CAPTCHA_CONFLICT);
  }

  @Test
  void consumedCaptchaPrecedesLookupAndMemberNotFoundCreatesNoSession() {
    consumedCaptcha();
    when(lookup.search(any()))
        .thenAnswer(
            i -> {
              calls.add("lookup");
              return Mono.just(
                  new UsernameRecoveryLookupResult(
                      UsernameRecoveryLookupResult.Status.NOT_FOUND, List.of()));
            });
    StepVerifier.create(execute())
        .assertNext(
            r -> {
              assertThat(r.outcome())
                  .isEqualTo(UsernameRecoveryStartResult.Outcome.MEMBER_NOT_FOUND);
              assertThat(r.authFlowSessionId()).isNull();
              assertThat(r.csrfToken()).isNull();
              assertThat(r.schemes()).isEmpty();
            })
        .verifyComplete();
    assertThat(calls).containsExactly("captcha", "lookup");
    verifyNoInteractions(sessions, contexts, csrfGenerator, csrfHasher);
  }

  @Test
  void zeroRegisteredReturnsSafeProjectionsAndCreatesNoSession() {
    consumedCaptcha();
    when(lookup.search(any()))
        .thenReturn(
            Mono.just(
                new UsernameRecoveryLookupResult(
                    UsernameRecoveryLookupResult.Status.MATCHED,
                    List.of(new UsernameRecoveryLookupCandidate("DB", "DBA", false, null, null, null)))));
    StepVerifier.create(execute())
        .assertNext(
            r -> {
              assertThat(r.outcome())
                  .isEqualTo(UsernameRecoveryStartResult.Outcome.ZERO_REGISTERED);
              assertThat(r.authFlowSessionId()).isNull();
              assertThat(r.csrfToken()).isNull();
              assertThat(r.schemes())
                  .singleElement()
                  .satisfies(
                      s -> {
                        assertThat(s.schemeRef()).isNull();
                        assertThat(s.registered()).isFalse();
                        assertThat(s.accountEnv()).isEqualTo("DB");
                        assertThat(s.trustCode()).isEqualTo("DBA");
                      });
            })
        .verifyComplete();
    verifyNoInteractions(sessions, contexts, csrfGenerator, csrfHasher);
  }

  @Test
  void registeredCandidatesCreateOneProtectedRevisionedEnvelopeAndTransientResult() {
    consumedCaptcha();
    when(lookup.search(any())).thenReturn(Mono.just(matched()));
    when(contexts.bind(any(), any(), any())).thenAnswer(i -> i.getArgument(0));
    when(sessions.createRevisioned(any()))
        .thenAnswer(
            i -> {
              calls.add("session");
              AuthFlowSessionCreateCommand c = i.getArgument(0);
              var session =
                  new AuthFlowSession(
                      c.authFlowSessionId(),
                      c.scenario(),
                      c.initialStatus(),
                      c.csrfTokenHash(),
                      c.activeOtpChallengeId(),
                      c.data(),
                      c.createdAt(),
                      c.createdAt(),
                      c.expiresAt());
              return Mono.just(
                  new AuthFlowSessionSnapshot(
                      session, new SessionRevision("0123456789abcdef0123456789abcdef")));
            });
    StepVerifier.create(execute())
        .assertNext(
            r -> {
              assertThat(r.outcome())
                  .isEqualTo(UsernameRecoveryStartResult.Outcome.SESSION_CREATED);
              assertThat(r.authFlowSessionId()).isNotNull();
              assertThat(r.csrfToken()).isEqualTo("csrfSecretValue");
              assertThat(r.expiresInSeconds()).isEqualTo(TTL);
              assertThat(r.schemes())
                  .hasSize(2)
                  .allSatisfy(s -> assertThat(s.schemeRef()).isNotNull());
              assertThat(r.toString())
                  .doesNotContain("csrfSecretValue", EMAIL, "3030609", "04/07/1978", "user-1");
            })
        .verifyComplete();
    assertThat(calls).containsExactly("captcha", "session");
    ArgumentCaptor<AuthFlowSessionCreateCommand> create =
        ArgumentCaptor.forClass(AuthFlowSessionCreateCommand.class);
    verify(sessions).createRevisioned(create.capture());
    assertThat(create.getValue().scenario()).isEqualTo(AuthFlowScenario.USERNAME_RECOVERY);
    assertThat(create.getValue().initialStatus()).isEqualTo(AuthFlowStatus.STARTED);
    assertThat(create.getValue().createdAt()).isEqualTo(NOW);
    assertThat(create.getValue().expiresAt()).isEqualTo(NOW.plusSeconds(TTL));
    assertThat(create.getValue().csrfTokenHash()).isEqualTo("csrfHashValue");
    verify(contexts)
        .bind(
            any(),
            argThat(
                c ->
                    c.language().equals(Locale.ENGLISH)
                        && c.candidates().size() == 2
                        && c.candidates().stream().allMatch(x -> x.schemeRef() != null)),
            eq(NOW));
  }

  @Test
  void lookupFailuresAndUnavailableStatusesNeverCreateSessionAndLeaveCaptchaConsumed() {
    for (var status :
        List.of(
            UsernameRecoveryLookupResult.Status.CONTRACT_INVALID,
            UsernameRecoveryLookupResult.Status.UNAVAILABLE,
            UsernameRecoveryLookupResult.Status.UNKNOWN_OUTCOME)) {
      reset(captcha, lookup, sessions);
      consumedCaptcha();
      when(lookup.search(any()))
          .thenReturn(Mono.just(new UsernameRecoveryLookupResult(status, List.of())));
      StepVerifier.create(execute()).expectError(UsernameRecoveryStartException.class).verify();
      verifyNoInteractions(sessions);
    }
    reset(captcha, lookup, sessions);
    consumedCaptcha();
    when(lookup.search(any())).thenReturn(Mono.error(new IllegalStateException("lookup failed")));
    StepVerifier.create(execute()).expectError(UsernameRecoveryStartException.class).verify();
    verifyNoInteractions(sessions);
  }

  @Test
  void sessionPersistenceFailureReturnsNoSuccessAfterCaptchaAndLookup() {
    consumedCaptcha();
    when(lookup.search(any())).thenReturn(Mono.just(matched()));
    when(contexts.bind(any(), any(), any())).thenAnswer(i -> i.getArgument(0));
    when(sessions.createRevisioned(any()))
        .thenReturn(Mono.error(new IllegalStateException("store failed")));
    StepVerifier.create(execute()).expectError(UsernameRecoveryStartException.class).verify();
    verify(captcha).consume(any());
    verify(lookup).search(any());
    verify(sessions).createRevisioned(any());
  }

  private void assertCaptchaReason(
      CaptchaConsumeOutcome outcome, UsernameRecoveryStartException.Reason reason) {
    reset(captcha, lookup, sessions, contexts, csrfGenerator, csrfHasher);
    when(captcha.consume(any()))
        .thenReturn(
            Mono.just(new ConsumeCaptchaChallengeResult(outcome, new CaptchaChallengeId(CAPTCHA))));
    StepVerifier.create(execute()).expectErrorSatisfies(e -> assertReason(e, reason)).verify();
    verifyNoInteractions(lookup, sessions, contexts, csrfGenerator, csrfHasher);
  }

  private void consumedCaptcha() {
    when(captcha.consume(any()))
        .thenAnswer(
            i -> {
              calls.add("captcha");
              return Mono.just(
                  new ConsumeCaptchaChallengeResult(
                      CaptchaConsumeOutcome.CONSUMED, new CaptchaChallengeId(CAPTCHA)));
            });
  }

  private Mono<UsernameRecoveryStartResult> execute() {
    var c = command(CAPTCHA);
    return operation.execute(c, validator.validate(c));
  }

  private static UsernameRecoveryStartCommand command(String captchaId) {
    return new UsernameRecoveryStartCommand(
        MemberIdentityType.STAFFNO,
        "3030609",
        "04/07/1978",
        EMAIL,
        null,
        captchaId,
        Locale.ENGLISH,
        "idem_12345678");
  }

  private static UsernameRecoveryLookupResult matched() {
    return new UsernameRecoveryLookupResult(
        UsernameRecoveryLookupResult.Status.MATCHED,
        List.of(
            new UsernameRecoveryLookupCandidate("DB", "DBA", true, "user-1", null, null),
            new UsernameRecoveryLookupCandidate("DB", "DBB", false, null, null, null)));
  }

  private static void assertReason(Throwable error, UsernameRecoveryStartException.Reason reason) {
    assertThat(error).isInstanceOf(UsernameRecoveryStartException.class);
    assertThat(((UsernameRecoveryStartException) error).reason()).isEqualTo(reason);
  }
}
