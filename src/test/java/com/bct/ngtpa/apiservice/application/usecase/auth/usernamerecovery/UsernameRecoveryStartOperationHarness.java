package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.security.usernamerecovery.AesGcmUsernameRecoveryAuthFlowContextProtectorAdapter;
import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaConsumeOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.ConsumeCaptchaChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryLookupCandidate;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryLookupResult;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCreateCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SessionRevision;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryLookupPort;
import com.bct.ngtpa.apiservice.application.service.auth.captcha.RegistrationCaptchaGateService;
import com.bct.ngtpa.apiservice.application.service.auth.usernamerecovery.context.UsernameRecoveryAuthFlowContextBinding;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenGenerator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenHasher;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.Sha256CsrfTokenHasher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.Mono;

/**
 * Test harness for the real {@link UsernameRecoveryStartProtectedOperation}: only the external
 * boundaries (captcha gate, lookup port unless a real one is supplied, auth-flow session store and
 * the raw CSRF source) are doubled. The protected context uses the real AES-GCM binding, so the
 * persisted continuity can be read back and asserted.
 */
public final class UsernameRecoveryStartOperationHarness {
  public static final Instant NOW = Instant.parse("2026-11-05T02:00:00Z");
  public static final int TTL = 600;
  public static final String CAPTCHA = "cap_f67f65980e944119b8b81c74d9a1834a";
  public static final String EMAIL = "member@example.com";
  public static final String RAW_CSRF = "csrfRawSecretValue";
  public static final String CSRF_HASH = new Sha256CsrfTokenHasher().hash(RAW_CSRF);
  private static final String CONTEXT_SECRET = "0123456789abcdef0123456789abcdef";

  public final RegistrationCaptchaGateService captcha = mock(RegistrationCaptchaGateService.class);
  public final UsernameRecoveryLookupPort lookup;
  public final AuthFlowSessionPort sessions = mock(AuthFlowSessionPort.class);
  public final CsrfTokenGenerator csrfGenerator = mock(CsrfTokenGenerator.class);
  public final CsrfTokenHasher csrfHasher = mock(CsrfTokenHasher.class);
  public final UsernameRecoveryAuthFlowContextBinding binding =
      new UsernameRecoveryAuthFlowContextBinding(
          new AesGcmUsernameRecoveryAuthFlowContextProtectorAdapter("v1", CONTEXT_SECRET));
  public final List<String> calls = new CopyOnWriteArrayList<>();
  public final AtomicReference<AuthFlowSessionSnapshot> stored = new AtomicReference<>();
  public final AtomicInteger creates = new AtomicInteger();
  public final AtomicInteger updates = new AtomicInteger();
  public final UsernameRecoveryStartProtectedOperation operation;

  private final boolean lookupIsMock;
  private final UsernameRecoveryStartValidator validator = new UsernameRecoveryStartValidator();

  public UsernameRecoveryStartOperationHarness() {
    this(null);
  }

  public UsernameRecoveryStartOperationHarness(UsernameRecoveryLookupPort realLookup) {
    this.lookupIsMock = realLookup == null;
    this.lookup = realLookup == null ? mock(UsernameRecoveryLookupPort.class) : realLookup;
    when(csrfGenerator.generate()).thenReturn(RAW_CSRF);
    when(csrfHasher.hash(RAW_CSRF)).thenReturn(CSRF_HASH);
    when(captcha.consume(any()))
        .thenAnswer(
            invocation -> {
              calls.add("captcha");
              return Mono.just(
                  new ConsumeCaptchaChallengeResult(
                      CaptchaConsumeOutcome.CONSUMED, new CaptchaChallengeId(CAPTCHA)));
            });
    when(sessions.createRevisioned(any()))
        .thenAnswer(
            invocation -> {
              AuthFlowSessionCreateCommand command = invocation.getArgument(0);
              calls.add("session");
              creates.incrementAndGet();
              var session =
                  new AuthFlowSession(
                      command.authFlowSessionId(),
                      command.scenario(),
                      command.initialStatus(),
                      command.csrfTokenHash(),
                      command.activeOtpChallengeId(),
                      command.data(),
                      command.createdAt(),
                      command.createdAt(),
                      command.expiresAt());
              var snapshot =
                  new AuthFlowSessionSnapshot(
                      session, new SessionRevision("0123456789abcdef0123456789abcdef"));
              stored.set(snapshot);
              return Mono.just(snapshot);
            });
    when(sessions.loadSnapshot(any()))
        .thenAnswer(invocation -> Mono.just(Optional.ofNullable(stored.get())));
    when(sessions.updateRevisioned(any()))
        .thenAnswer(
            invocation -> {
              updates.incrementAndGet();
              return Mono.error(new AssertionError("start flow must never update a session"));
            });
    this.operation =
        new UsernameRecoveryStartProtectedOperation(
            captcha,
            lookup,
            sessions,
            binding,
            csrfGenerator,
            csrfHasher,
            Clock.fixed(NOW, ZoneOffset.UTC),
            TTL);
  }

  public void lookupReturns(UsernameRecoveryLookupResult result) {
    if (!lookupIsMock) {
      throw new IllegalStateException("a real lookup port was supplied");
    }
    when(lookup.search(any()))
        .thenAnswer(
            invocation -> {
              calls.add("lookup");
              return Mono.just(result);
            });
  }

  public void sessionCreationFails() {
    doReturn(Mono.error(new IllegalStateException("store failed")))
        .when(sessions)
        .createRevisioned(any());
  }

  public Mono<UsernameRecoveryStartResult> execute() {
    UsernameRecoveryStartCommand command = command();
    return operation.execute(command, validator.validate(command));
  }

  public AuthFlowSession storedSession() {
    return stored.get().session();
  }

  public static UsernameRecoveryStartCommand command() {
    return new UsernameRecoveryStartCommand(
        MemberIdentityType.STAFFNO,
        "3030609",
        "04/07/1978",
        EMAIL,
        null,
        CAPTCHA,
        Locale.ENGLISH,
        "idem_12345678");
  }

  public static UsernameRecoveryLookupCandidate unregistered(String env, String trustCode) {
    return new UsernameRecoveryLookupCandidate(env, trustCode, false, null, null, null);
  }

  public static UsernameRecoveryLookupCandidate registered(
      String env, String trustCode, String userName) {
    return new UsernameRecoveryLookupCandidate(env, trustCode, true, userName, null, null);
  }

  public static UsernameRecoveryLookupResult matched(UsernameRecoveryLookupCandidate... candidates) {
    return new UsernameRecoveryLookupResult(
        UsernameRecoveryLookupResult.Status.MATCHED, List.of(candidates));
  }
}
