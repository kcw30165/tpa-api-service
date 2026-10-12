package com.bct.ngtpa.apiservice.integration.forgotpassword;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.notice.YamlNoticeTemplateRegistryAdapter;
import com.bct.ngtpa.apiservice.adapter.out.notice.config.NoticeTemplateProperties;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordProtectedPhase;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.CreateOtpChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChannel;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpScenario;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.ReplaceOtpChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.VerifyOtpChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.message.EmailMessage;
import com.bct.ngtpa.apiservice.application.dto.message.SenderResult;
import com.bct.ngtpa.apiservice.application.dto.message.SenderStatus;
import com.bct.ngtpa.apiservice.application.dto.message.SmsMessage;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationOutcome;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationResult;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SessionRevision;
import com.bct.ngtpa.apiservice.application.port.in.EvaluateForgotPasswordOtpResendUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionMutationLockPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.EmailSenderPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordDetailsLookupPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordOtpCommitStatePort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordOtpSessionBindingPort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengePort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengeStatePort;
import com.bct.ngtpa.apiservice.application.port.out.SmsSenderPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp.ForgotPasswordDetailsOtpCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp.ForgotPasswordDetailsOtpLeasePolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp.ForgotPasswordDetailsOtpProtectedOperation;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp.ForgotPasswordDetailsOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp.ForgotPasswordUnderLockDetailsVerifier;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.ForgotPasswordDetailsLookupResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.ForgotPasswordDetailsLookupStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordInitialOtpPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpBindingResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpCommitRetentionPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpCommitState;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.SecureForgotPasswordOtpCommitRefGenerator;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ForgotPasswordOtpResendEligibilityStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ForgotPasswordOtpResendProtectedOperation;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ForgotPasswordOtpResendResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ResendForgotPasswordOtpCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ResendForgotPasswordOtpStatus;
import com.bct.ngtpa.apiservice.application.usecase.message.MessageDeliveryAdapter;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import reactor.core.publisher.Mono;

/**
 * Forgot Password OTP content end to end (plan stage II-4.c and II-4.d combined, per Nick's
 * instruction to combine the two sub-stages the same way Login's II-3.c/II-3.d were combined,
 * script 108, Appendix E.10): the REAL {@code application.yml}, the REAL {@link
 * YamlNoticeTemplateRegistryAdapter}, the REAL {@link ForgotPasswordDetailsOtpProtectedOperation}
 * (send) / {@link ForgotPasswordOtpResendProtectedOperation} (resend) (as completed by scripts
 * 132 and 134: {@code trustCode}/{@code schemeName}/{@code schemeNameChi} are threaded from the
 * context into the rendered content, with a blank {@code schemeNameChi} falling back to {@code
 * schemeName}, and the registry definition is resolved before any OTP challenge is created or
 * session mutated), and the REAL {@link MessageDeliveryAdapter} with its default renderer. Every
 * other collaborator (session port, lock port, protector, lookup port, OTP challenge port,
 * binding port, commit-state port, eligibility use case) is mocked - exactly the
 * infrastructure-backed ports the two pre-existing unit tests for these classes already mock
 * ({@link com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp.ForgotPasswordDetailsOtpOrchestrationSubtask005004FailFirstTest},
 * {@link com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ForgotPasswordOtpResendProtectedOperationTest})
 * - so the text that would reach a member can be asserted for SMS and EMAIL in en and zh_HK
 * (II-4.c), and so a missing registry definition is proven to fail before any OTP challenge is
 * created or, for resend, any session mutation is persisted (II-4.d).
 *
 * <p>Per solution design section 5 (confirmed in Appendix E.5), {@code forgot-password-otp}'s YAML
 * content is byte-identical to {@code login-otp}'s (synchronized wording, two separate config
 * entries) - confirmed directly against the real {@code application.yml} (script 124). This test
 * therefore asserts the same literal wording Login's own rendered-content test already proved
 * (e.g. "One-Time Password", not "forgot password" phrasing), which is the correct, by-design
 * outcome, not an oversight.
 *
 * <p>{@code trustCode}/{@code schemeName}/{@code schemeNameChi} values used below are
 * representative test fixtures (no specific real production example was provided for Forgot
 * Password, unlike Login's real {@code TRPMemberLogin} example) - they are chosen only to exercise
 * every placeholder and the blank-{@code schemeNameChi} fallback rule.
 */
class ForgotPasswordOtpRenderedContentIntegrationTest {

  private static final String TRUST_CODE = "HSBC";
  private static final String SCHEME_NAME = "HSBC PROVIDENT FUND TRUST";
  private static final String SCHEME_NAME_CHI = "滙豐公積金信託計劃";

  private static NoticeTemplateProperties realProperties() {
    YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
    List<PropertySource<?>> sources;
    try {
      sources = loader.load("application.yml", new ClassPathResource("application.yml"));
    } catch (Exception e) {
      throw new IllegalStateException("failed to load application.yml from test classpath", e);
    }
    MutablePropertySources propertySources = new MutablePropertySources();
    sources.forEach(propertySources::addLast);
    Binder binder = new Binder(ConfigurationPropertySources.from(propertySources));
    return binder
        .bind("notice-templates", NoticeTemplateProperties.class)
        .orElseThrow(
            () -> new IllegalStateException("notice-templates did not bind from application.yml"));
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  // ===============================================================================
  // SEND (Details/OTP) path - mirrors the real construction order already proven in
  // ForgotPasswordDetailsOtpOrchestrationSubtask005004FailFirstTest, parameterized by
  // trustCode/schemeName/schemeNameChi/ttlSeconds, with registry+delivery swapped for a real
  // YamlNoticeTemplateRegistryAdapter and capturing senders instead of mocks.
  // ===============================================================================
  private static final class SendHarness {
    final AtomicInteger smsCalls = new AtomicInteger();
    final AtomicInteger emailCalls = new AtomicInteger();
    final AtomicReference<SmsMessage> sms = new AtomicReference<>();
    final AtomicReference<EmailMessage> email = new AtomicReference<>();
    final OtpChallengePort otp = mock(OtpChallengePort.class);
    final MessageDeliveryAdapter delivery;
    private final ForgotPasswordDetailsOtpProtectedOperation service;

    SendHarness(
        NoticeTemplateProperties properties,
        String trustCode,
        String schemeName,
        String schemeNameChi,
        int ttlSeconds) {
      this(
          properties,
          trustCode,
          schemeName,
          schemeNameChi,
          ttlSeconds,
          new ForgotPasswordDetailsLookupResult(ForgotPasswordDetailsLookupStatus.MATCHED));
    }

    SendHarness(
        NoticeTemplateProperties properties,
        String trustCode,
        String schemeName,
        String schemeNameChi,
        int ttlSeconds,
        ForgotPasswordDetailsLookupResult lookupResult) {
      Instant now = Instant.parse("2026-08-04T10:15:30Z");
      Clock clock = Clock.fixed(now, ZoneOffset.UTC);
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
              null,
              null,
              trustCode,
              schemeName,
              schemeNameChi,
              "v1",
              ForgotPasswordProtectedPhase.IDENTITY_VERIFIED);

      AuthFlowSessionMutationLockPort locks = mock(AuthFlowSessionMutationLockPort.class);
      when(locks.acquire(any(), any())).thenReturn(Mono.just(lock));
      when(locks.release(lock)).thenReturn(Mono.just(true));

      AuthFlowSessionPort sessions = mock(AuthFlowSessionPort.class);
      when(sessions.loadSnapshot(sessionId))
          .thenReturn(
              Mono.just(
                  Optional.of(
                      new AuthFlowSessionSnapshot(
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
                              now.plusSeconds(300)),
                          new SessionRevision("0123456789abcdef0123456789abcdef")))));

      ForgotPasswordAuthFlowContextProtectorPort protector =
          mock(ForgotPasswordAuthFlowContextProtectorPort.class);
      when(protector.unprotect(any(ForgotPasswordAuthFlowEncryptedContext.class)))
          .thenReturn(context);

      ForgotPasswordDetailsLookupPort lookup = mock(ForgotPasswordDetailsLookupPort.class);
      when(lookup.verify(any())).thenReturn(Mono.just(lookupResult));

      when(otp.createChallenge(any()))
          .thenReturn(
              Mono.just(
                  new CreateOtpChallengeResult(
                      "otp-1", "123456", now.plusSeconds(ttlSeconds), ttlSeconds)));

      SmsSenderPort smsSender =
          message -> {
            smsCalls.incrementAndGet();
            sms.set(message);
            return Mono.just(new SenderResult(SenderStatus.ACCEPTED, "provider-sms-id", null, null));
          };
      EmailSenderPort emailSender =
          message -> {
            emailCalls.incrementAndGet();
            email.set(message);
            return Mono.just(
                new SenderResult(SenderStatus.ACCEPTED, "provider-email-id", null, null));
          };
      delivery = new MessageDeliveryAdapter(smsSender, emailSender);

      ForgotPasswordOtpSessionBindingPort binding = mock(ForgotPasswordOtpSessionBindingPort.class);
      when(binding.apply(any()))
          .thenReturn(
              Mono.just(new ForgotPasswordOtpBindingResult(ForgotPasswordOtpBindingResult.Status.BOUND)));

      ForgotPasswordOtpCommitStatePort commitStates = new InMemoryCommitStates();
      YamlNoticeTemplateRegistryAdapter registry = new YamlNoticeTemplateRegistryAdapter(properties);

      service =
          new ForgotPasswordDetailsOtpProtectedOperation(
              new SecureForgotPasswordOtpCommitRefGenerator(),
              locks,
              sessions,
              protector,
              new ForgotPasswordUnderLockDetailsVerifier(lookup),
              otp,
              delivery,
              new MaskingService(new MaskingProperties()),
              binding,
              commitStates,
              new ForgotPasswordOtpCommitRetentionPolicy(
                  Duration.ofSeconds(60), Duration.ofMinutes(10)),
              new ForgotPasswordDetailsOtpLeasePolicy(60, 10, 10, 3, 10000, 100, 5000, 1000),
              clock,
              Duration.ofMinutes(5),
              registry);
    }

    ForgotPasswordDetailsOtpResult execute(String email, String mobile, String language) {
      return service
          .execute(
              new ForgotPasswordDetailsOtpCommand(
                  new AuthFlowSessionId("afs_test_123"),
                  "csrf",
                  "https://portal.example",
                  "https://portal.example/forgot-password",
                  "req-123",
                  language,
                  "ac2e9b54-836d-4729-8957-d89b1d8ae015",
                  "04/07/1978",
                  email,
                  mobile))
          .block()
          .result();
    }

    private static final class InMemoryCommitStates implements ForgotPasswordOtpCommitStatePort {
      private final Map<String, ForgotPasswordOtpCommitState> states = new java.util.HashMap<>();

      @Override
      public Mono<Boolean> saveIfAbsent(
          String opaqueCommitRef, ForgotPasswordOtpCommitState state, Duration ttl) {
        return Mono.just(states.putIfAbsent(opaqueCommitRef, state) == null);
      }

      @Override
      public Mono<Optional<ForgotPasswordOtpCommitState>> load(String opaqueCommitRef) {
        return Mono.just(Optional.ofNullable(states.get(opaqueCommitRef)));
      }

      @Override
      public Mono<Void> delete(String opaqueCommitRef) {
        states.remove(opaqueCommitRef);
        return Mono.empty();
      }
    }
  }

  // ===============================================================================
  // RESEND (OTP_RESEND) path - mirrors the real construction order already proven in
  // ForgotPasswordOtpResendProtectedOperationTest, parameterized by
  // trustCode/schemeName/schemeNameChi, with registry+delivery swapped for a real
  // YamlNoticeTemplateRegistryAdapter and capturing senders instead of mocks.
  // ===============================================================================
  private static final class ResendHarness {
    private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");
    private static final String ALLOWED_ORIGIN = "https://portal.bcthk.com";
    private static final String RAW_CSRF = "csrf-raw-token";
    private static final String PROTECTED_DESTINATION = "jane@example.com";
    private static final String MASKED_DESTINATION = "j***@example.com";

    final AtomicInteger smsCalls = new AtomicInteger();
    final AtomicInteger emailCalls = new AtomicInteger();
    final AtomicReference<SmsMessage> sms = new AtomicReference<>();
    final AtomicReference<EmailMessage> email = new AtomicReference<>();

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final AuthFlowSessionId sid = new AuthFlowSessionId("afs-otp-1");
    private final String csrfHash = sha256(RAW_CSRF);
    private final ForgotPasswordAuthFlowContext context;

    final AuthFlowSessionMutationLockPort locks = mock(AuthFlowSessionMutationLockPort.class);
    final AuthFlowSessionPort sessions = mock(AuthFlowSessionPort.class);
    final ForgotPasswordAuthFlowContextProtectorPort protector =
        mock(ForgotPasswordAuthFlowContextProtectorPort.class);
    final EvaluateForgotPasswordOtpResendUseCase foundation =
        mock(EvaluateForgotPasswordOtpResendUseCase.class);
    final OtpChallengePort otp = mock(OtpChallengePort.class);
    final OtpChallengeStatePort challengeState = mock(OtpChallengeStatePort.class);
    final MessageDeliveryAdapter delivery;
    final ForgotPasswordInitialOtpPolicy otpPolicy = ForgotPasswordInitialOtpPolicy.approved();
    final SecureForgotPasswordOtpCommitRefGenerator refs =
        mock(SecureForgotPasswordOtpCommitRefGenerator.class);
    private final ForgotPasswordOtpResendProtectedOperation service;

    ResendHarness(NoticeTemplateProperties properties, String trustCode, String schemeName,
        String schemeNameChi) {
      context =
          new ForgotPasswordAuthFlowContext(
              "jane",
              "idv-1",
              "CUSTID",
              true,
              "subj-1",
              "email",
              PROTECTED_DESTINATION,
              MASKED_DESTINATION,
              null,
              null,
              trustCode,
              schemeName,
              schemeNameChi,
              "v1",
              ForgotPasswordProtectedPhase.OTP_SENT);

      SmsSenderPort smsSender =
          message -> {
            smsCalls.incrementAndGet();
            sms.set(message);
            return Mono.just(new SenderResult(SenderStatus.ACCEPTED, "provider-sms-id", null, null));
          };
      EmailSenderPort emailSender =
          message -> {
            emailCalls.incrementAndGet();
            email.set(message);
            return Mono.just(
                new SenderResult(SenderStatus.ACCEPTED, "provider-email-id", null, null));
          };
      delivery = new MessageDeliveryAdapter(smsSender, emailSender);

      MaskingService masker = mock(MaskingService.class);
      when(masker.maskEmail(any())).thenReturn(MASKED_DESTINATION);

      ForgotPasswordOtpCommitStatePort commitStates = mock(ForgotPasswordOtpCommitStatePort.class);
      ForgotPasswordOtpCommitRetentionPolicy retention =
          new ForgotPasswordOtpCommitRetentionPolicy(Duration.ofMinutes(5), Duration.ofHours(1));
      YamlNoticeTemplateRegistryAdapter registry = new YamlNoticeTemplateRegistryAdapter(properties);

      service =
          new ForgotPasswordOtpResendProtectedOperation(
              refs,
              locks,
              sessions,
              protector,
              foundation,
              otp,
              challengeState,
              delivery,
              masker,
              commitStates,
              retention,
              registry,
              otpPolicy,
              clock,
              Duration.ofMinutes(10),
              Duration.ofSeconds(30),
              List.of(ALLOWED_ORIGIN));

      // ----- real default eligible-and-dispatch stubbing, mirrored from the pre-existing
      // test's givenEligible(): lock, session, context, challenge, session-mutation-applied,
      // foundation=ELIGIBLE, otp.createChallenge, protector.protect, defensive otp/challengeState
      // stubs. registry is REAL here, not stubbed. -----
      when(locks.acquire(any(), any()))
          .thenReturn(Mono.just(new SessionLockHandle(true, "lock-key", "lock-token")));
      when(locks.release(any())).thenReturn(Mono.just(true));

      AuthFlowSession session =
          new AuthFlowSession(
              sid,
              AuthFlowScenario.FORGOT_PASSWORD,
              AuthFlowStatus.OTP_SENT,
              csrfHash,
              "challenge-1",
              new AuthFlowSessionData(
                  "email",
                  MASKED_DESTINATION,
                  Map.of(
                      "forgotPasswordContextCiphertext", "ciphertext-1",
                      "forgotPasswordContextKeyVersion", "kv-1",
                      "forgotPasswordContextAlgorithm",
                          ForgotPasswordAuthFlowEncryptedContext.DEFAULT_ALGORITHM)),
              NOW.minusSeconds(120),
              NOW.minusSeconds(60),
              NOW.plusSeconds(1800));
      when(sessions.loadSnapshot(any()))
          .thenReturn(
              Mono.just(
                  Optional.of(
                      new AuthFlowSessionSnapshot(session, new SessionRevision("0".repeat(32))))));

      when(protector.unprotect(any())).thenReturn(context);

      OtpChallenge validChallenge =
          new OtpChallenge(
              "challenge-1",
              OtpScenario.FORGOT_PASSWORD,
              sid.value(),
              OtpChannel.EMAIL,
              sha256(PROTECTED_DESTINATION),
              "codehash",
              OtpStatus.CREATED,
              0,
              5,
              0,
              5,
              NOW.minusSeconds(120),
              NOW.plusSeconds(180),
              NOW.minusSeconds(120),
              null,
              null);
      when(challengeState.findById(any())).thenReturn(Mono.just(Optional.of(validChallenge)));

      when(sessions.updateRevisioned(any()))
          .thenAnswer(
              invocation ->
                  Mono.just(
                      new AuthFlowSessionMutationResult(
                          AuthFlowSessionMutationOutcome.APPLIED,
                          new AuthFlowSessionSnapshot(
                              session, new SessionRevision("1".repeat(32))))));

      when(refs.generate()).thenReturn("pending-ref-1234567890");

      when(foundation.evaluate(any()))
          .thenReturn(
              Mono.just(
                  new ForgotPasswordOtpResendResult(
                      ForgotPasswordOtpResendEligibilityStatus.ELIGIBLE, null, 0)));

      when(otp.createChallenge(any()))
          .thenReturn(
              Mono.just(
                  new CreateOtpChallengeResult(
                      "candidate-1", "123456", NOW.plusSeconds(otpPolicy.ttlSeconds()),
                      otpPolicy.ttlSeconds())));

      when(protector.protect(any()))
          .thenReturn(new ForgotPasswordAuthFlowEncryptedContext("ct-2", "kv-2", "AES_GCM_V1"));

      // --- Defensive stubbing, mirrored verbatim from the pre-existing test's givenEligible():
      // ALL Mono-returning methods on `otp` and `challengeState`, so no unstubbed-null-Mono NPE
      // can occur regardless of which specific port method the private helpers invoke. ---
      when(otp.invalidateChallenge(any())).thenReturn(Mono.empty());
      when(otp.verifyChallenge(any())).thenReturn(Mono.just(mock(VerifyOtpChallengeResult.class)));
      when(otp.replaceChallenge(any())).thenReturn(Mono.just(mock(ReplaceOtpChallengeResult.class)));
      when(challengeState.save(any())).thenReturn(Mono.empty());
      when(challengeState.update(any())).thenReturn(Mono.empty());
      when(challengeState.delete(any())).thenReturn(Mono.empty());
      when(commitStates.saveIfAbsent(any(), any(), any())).thenReturn(Mono.just(true));
      when(commitStates.load(any()))
          .thenAnswer(
              invocation ->
                  Mono.just(
                      Optional.of(
                          new ForgotPasswordOtpCommitState(
                              sid.value(),
                              "candidate-1",
                              NOW,
                              NOW.plusSeconds(otpPolicy.ttlSeconds())))));
      when(commitStates.delete(any())).thenReturn(Mono.empty());
    }

    ResendForgotPasswordOtpStatus execute(String language) {
      return service
          .execute(
              new ResendForgotPasswordOtpCommand(
                  sid, RAW_CSRF, ALLOWED_ORIGIN, null, "req-1", language, "idem-1"))
          .block()
          .result()
          .status();
    }
  }

  // ===============================================================================
  // II-4.c: rendering, on the real registry and real renderer, for SMS and EMAIL in en and zh_HK
  // ===============================================================================

  @Test
  void sendRendersSuccessfullyWithPopulatedSchemeNameChiOnAllFourChannels() {
    NoticeTemplateProperties properties = realProperties();

    SendHarness sms = new SendHarness(properties, TRUST_CODE, SCHEME_NAME, SCHEME_NAME_CHI, 300);
    assertThat(sms.execute(null, "91234567", "en").status())
        .isEqualTo(ForgotPasswordDetailsOtpResult.Status.SUCCESS);
    assertThat(sms.sms.get().content()).contains(TRUST_CODE).doesNotContain("{", "}");

    SendHarness smsZh = new SendHarness(properties, TRUST_CODE, SCHEME_NAME, SCHEME_NAME_CHI, 300);
    assertThat(smsZh.execute(null, "91234567", "zh-HK").status())
        .isEqualTo(ForgotPasswordDetailsOtpResult.Status.SUCCESS);
    assertThat(smsZh.sms.get().content()).contains(TRUST_CODE).doesNotContain("{", "}");

    SendHarness emailEn =
        new SendHarness(properties, TRUST_CODE, SCHEME_NAME, SCHEME_NAME_CHI, 300);
    assertThat(emailEn.execute("member@example.com", null, "en").status())
        .isEqualTo(ForgotPasswordDetailsOtpResult.Status.SUCCESS);
    assertThat(emailEn.email.get().subject()).contains(TRUST_CODE);
    assertThat(emailEn.email.get().body()).contains(SCHEME_NAME).doesNotContain("{", "}");

    SendHarness emailZh =
        new SendHarness(properties, TRUST_CODE, SCHEME_NAME, SCHEME_NAME_CHI, 300);
    assertThat(emailZh.execute("member@example.com", null, "zh-HK").status())
        .isEqualTo(ForgotPasswordDetailsOtpResult.Status.SUCCESS);
    assertThat(emailZh.email.get().subject()).doesNotContain(TRUST_CODE).doesNotContain("{", "}");
    assertThat(emailZh.email.get().body()).contains(SCHEME_NAME_CHI).doesNotContain("{", "}");
  }

  @Test
  void resendRendersCorrectlyOnEmailInEnAndZhHk() {
    NoticeTemplateProperties properties = realProperties();

    ResendHarness en = new ResendHarness(properties, TRUST_CODE, SCHEME_NAME, SCHEME_NAME_CHI);
    assertThat(en.execute("en")).isEqualTo(ResendForgotPasswordOtpStatus.RESENT);
    assertThat(en.email.get().subject()).contains(TRUST_CODE).doesNotContain("{", "}");
    assertThat(en.email.get().body()).contains(SCHEME_NAME).doesNotContain("{", "}");

    ResendHarness zh = new ResendHarness(properties, TRUST_CODE, SCHEME_NAME, SCHEME_NAME_CHI);
    assertThat(zh.execute("zh-HK")).isEqualTo(ResendForgotPasswordOtpStatus.RESENT);
    assertThat(zh.email.get().subject()).doesNotContain(TRUST_CODE).doesNotContain("{", "}");
    assertThat(zh.email.get().body()).contains(SCHEME_NAME_CHI).doesNotContain("{", "}");
  }

  // ----- blank schemeNameChi falls back to schemeName (scripts 102/132's rule, mirrored) -------

  @Test
  void sendFallsBackToSchemeNameWhenSchemeNameChiIsBlank() {
    SendHarness harness = new SendHarness(realProperties(), TRUST_CODE, SCHEME_NAME, "", 300);

    assertThat(harness.execute("member@example.com", null, "zh-HK").status())
        .isEqualTo(ForgotPasswordDetailsOtpResult.Status.SUCCESS);
    assertThat(harness.email.get().body()).contains(SCHEME_NAME).doesNotContain("{", "}");
  }

  @Test
  void resendFallsBackToSchemeNameWhenSchemeNameChiIsBlank() {
    ResendHarness harness = new ResendHarness(realProperties(), TRUST_CODE, SCHEME_NAME, "");

    assertThat(harness.execute("zh-HK")).isEqualTo(ResendForgotPasswordOtpStatus.RESENT);
    assertThat(harness.email.get().body()).contains(SCHEME_NAME).doesNotContain("{", "}");
  }

  @Test
  void aPopulatedSchemeNameChiIsRenderedAsIsNotReplacedByTheFallback() {
    SendHarness harness =
        new SendHarness(realProperties(), TRUST_CODE, SCHEME_NAME, "示例計劃", 300);

    assertThat(harness.execute("member@example.com", null, "zh-HK").status())
        .isEqualTo(ForgotPasswordDetailsOtpResult.Status.SUCCESS);
    assertThat(harness.email.get().body())
        .contains("示例計劃")
        .doesNotContain(SCHEME_NAME)
        .doesNotContain("{", "}");
  }

  // ----- unit and value: minutes, Chinese unit placement, whole-minutes-floor-of-one (D8/D9) ---

  @Test
  void hongKongSmsWritesTheChineseUnitDirectlyAfterTheNumberWithNoLeftoverPlaceholder() {
    SendHarness harness =
        new SendHarness(realProperties(), TRUST_CODE, SCHEME_NAME, SCHEME_NAME_CHI, 300);

    assertThat(harness.execute(null, "91234567", "zh-HK").status())
        .isEqualTo(ForgotPasswordDetailsOtpResult.Status.SUCCESS);
    assertThat(harness.sms.get().content())
        .contains("123456")
        .contains("有效期為5分鐘。")
        .doesNotContain("minutes")
        .doesNotContain("{", "}");
  }

  @Test
  void zhHkAndEnglishSmsContentActuallyDiffer() {
    SendHarness english =
        new SendHarness(realProperties(), TRUST_CODE, SCHEME_NAME, SCHEME_NAME_CHI, 300);
    english.execute(null, "91234567", "en");

    SendHarness chinese =
        new SendHarness(realProperties(), TRUST_CODE, SCHEME_NAME, SCHEME_NAME_CHI, 300);
    chinese.execute(null, "91234567", "zh-HK");

    assertThat(chinese.sms.get().content()).isNotEqualTo(english.sms.get().content());
  }

  @Test
  void ttlValueIsWholeMinutesWithAMinimumOfOne() {
    int[][] cases = {{1, 1}, {30, 1}, {119, 1}, {120, 2}, {300, 5}, {600, 10}};
    NoticeTemplateProperties properties = realProperties();

    for (int[] pair : cases) {
      SendHarness harness =
          new SendHarness(properties, TRUST_CODE, SCHEME_NAME, SCHEME_NAME_CHI, pair[0]);
      harness.execute(null, "91234567", "en");
      assertThat(harness.sms.get().content())
          .as("sms for %d seconds", pair[0])
          .contains("Valid for " + pair[1] + " ");
    }
  }

  // ===============================================================================
  // II-4.d: a missing registry definition fails before any OTP challenge is created, or (resend)
  // before any session mutation is persisted - closed by script 134's reordering fix.
  // ===============================================================================

  @Test
  void aMissingForgotPasswordOtpDefinitionFailsSendBeforeAnyChallengeIsCreatedOrMessageSent() {
    NoticeTemplateProperties properties = realProperties();
    properties.setDefinitions(new LinkedHashMap<>());
    SendHarness harness = new SendHarness(properties, TRUST_CODE, SCHEME_NAME, SCHEME_NAME_CHI, 300);

    assertThat(harness.execute("member@example.com", null, "en").status())
        .isEqualTo(ForgotPasswordDetailsOtpResult.Status.SYSTEM_ERROR);
    verify(harness.otp, never()).createChallenge(any());
    assertThat(harness.emailCalls.get()).isZero();
    assertThat(harness.smsCalls.get()).isZero();
  }

  @Test
  void
      aMissingForgotPasswordOtpDefinitionFailsResendBeforeAnyChallengeIsCreatedOrSessionIsMutatedOrMessageSent() {
    NoticeTemplateProperties properties = realProperties();
    properties.setDefinitions(new LinkedHashMap<>());
    ResendHarness harness = new ResendHarness(properties, TRUST_CODE, SCHEME_NAME, SCHEME_NAME_CHI);

    assertThat(harness.execute("en")).isEqualTo(ResendForgotPasswordOtpStatus.SYSTEM_FAILED);
    verify(harness.otp, never()).createChallenge(any());
    verify(harness.sessions, never()).updateRevisioned(any());
    assertThat(harness.emailCalls.get()).isZero();
    assertThat(harness.smsCalls.get()).isZero();
  }

  // ===============================================================================
  // Live-path regression (scripts 173-179): the protected context is created at START with no
  // trustCode/schemeName/schemeNameChi, and nothing on the live DETAILS_AND_OTP path writes them
  // later, so the under-lock details step must take them from the TRPSearchMember lookup result.
  // These tests build the context the way START does (all three null) and supply the values only
  // through the lookup result.
  // ===============================================================================

  private static ForgotPasswordDetailsLookupResult matchedLookupWithTrustFields() {
    return new ForgotPasswordDetailsLookupResult(
        ForgotPasswordDetailsLookupStatus.MATCHED,
        "ENV1",
        TRUST_CODE,
        SCHEME_NAME,
        SCHEME_NAME_CHI);
  }

  @Test
  void sendRendersEmailFromLookupTrustFieldsWhenTheStoredContextHasNone() {
    SendHarness harness =
        new SendHarness(realProperties(), null, null, null, 300, matchedLookupWithTrustFields());

    assertThat(harness.execute("member@example.com", null, "en").status())
        .isEqualTo(ForgotPasswordDetailsOtpResult.Status.SUCCESS);
    assertThat(harness.email.get().subject()).contains(TRUST_CODE).doesNotContain("{", "}");
    assertThat(harness.email.get().body()).contains(SCHEME_NAME).doesNotContain("{", "}");
  }

  @Test
  void sendRendersZhHkEmailFromLookupTrustFieldsWhenTheStoredContextHasNone() {
    SendHarness harness =
        new SendHarness(realProperties(), null, null, null, 300, matchedLookupWithTrustFields());

    assertThat(harness.execute("member@example.com", null, "zh-HK").status())
        .isEqualTo(ForgotPasswordDetailsOtpResult.Status.SUCCESS);
    assertThat(harness.email.get().body()).contains(SCHEME_NAME_CHI).doesNotContain("{", "}");
  }

  @Test
  void sendRendersSmsFromLookupTrustFieldsWhenTheStoredContextHasNone() {
    SendHarness harness =
        new SendHarness(realProperties(), null, null, null, 300, matchedLookupWithTrustFields());

    assertThat(harness.execute(null, "91234567", "en").status())
        .isEqualTo(ForgotPasswordDetailsOtpResult.Status.SUCCESS);
    assertThat(harness.sms.get().content()).contains(TRUST_CODE).doesNotContain("{", "}");
  }

  // ===============================================================================
  // Persistence regression (scripts 180-181): resend reads trustCode/schemeName/schemeNameChi from
  // the protected context that was STORED at OTP_SENT, so they must be persisted by the details
  // check. These tests run the REAL details operation and the REAL OTP session binding adapter over
  // an in-memory session store and assert on the context that was persisted.
  // ===============================================================================

  @Test
  void detailsCheckPersistsTheLookupTrustFieldsForResend() {
    PersistedContextHarness harness =
        new PersistedContextHarness(
            realProperties(), null, null, null, null, matchedLookupWithTrustFields());

    assertThat(harness.execute("member@example.com", "en").status())
        .as("details-check outcome")
        .isEqualTo(ForgotPasswordDetailsOtpResult.Status.SUCCESS);

    ForgotPasswordAuthFlowContext persisted = harness.persistedContext();
    assertThat(persisted.phase())
        .as("persisted phase")
        .isEqualTo(ForgotPasswordProtectedPhase.OTP_SENT);
    assertThat(persisted.trustCode()).as("persisted trustCode").isEqualTo(TRUST_CODE);
    assertThat(persisted.schemeName()).as("persisted schemeName").isEqualTo(SCHEME_NAME);
    assertThat(persisted.schemeNameChi()).as("persisted schemeNameChi").isEqualTo(SCHEME_NAME_CHI);
  }

  @Test
  void detailsCheckKeepsTrustFieldsAlreadyStoredWhenTheLookupSuppliesNone() {
    PersistedContextHarness harness =
        new PersistedContextHarness(
            realProperties(),
            "ENV1",
            TRUST_CODE,
            SCHEME_NAME,
            SCHEME_NAME_CHI,
            new ForgotPasswordDetailsLookupResult(ForgotPasswordDetailsLookupStatus.MATCHED));

    assertThat(harness.execute("member@example.com", "en").status())
        .as("details-check outcome")
        .isEqualTo(ForgotPasswordDetailsOtpResult.Status.SUCCESS);

    ForgotPasswordAuthFlowContext persisted = harness.persistedContext();
    assertThat(persisted.phase())
        .as("persisted phase")
        .isEqualTo(ForgotPasswordProtectedPhase.OTP_SENT);
    assertThat(persisted.accountEnv()).as("persisted accountEnv").isEqualTo("ENV1");
    assertThat(persisted.trustCode()).as("persisted trustCode").isEqualTo(TRUST_CODE);
    assertThat(persisted.schemeName()).as("persisted schemeName").isEqualTo(SCHEME_NAME);
    assertThat(persisted.schemeNameChi()).as("persisted schemeNameChi").isEqualTo(SCHEME_NAME_CHI);
  }

  private static final class PersistedContextHarness {
    private static final Instant NOW = Instant.parse("2026-08-04T10:15:30Z");
    private static final AuthFlowSessionId SESSION_ID = new AuthFlowSessionId("afs_persist_123");
    private static final String CIPHERTEXT_KEY = "forgotPasswordContextCiphertext";

    private final AtomicReference<AuthFlowSessionSnapshot> store = new AtomicReference<>();
    private final Map<String, ForgotPasswordAuthFlowContext> vault = new java.util.HashMap<>();
    private final AtomicInteger contextSeq = new AtomicInteger();
    private final AtomicInteger revisionSeq = new AtomicInteger();
    private final ForgotPasswordDetailsOtpProtectedOperation service;

    PersistedContextHarness(
        NoticeTemplateProperties properties,
        String accountEnv,
        String trustCode,
        String schemeName,
        String schemeNameChi,
        ForgotPasswordDetailsLookupResult lookupResult) {
      Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
      SessionLockHandle lock = new SessionLockHandle(true, "lock-key", "owner-token");

      // The stored context as START leaves it: no contact data and, unless a test says otherwise,
      // no trust fields.
      ForgotPasswordAuthFlowContext initial =
          new ForgotPasswordAuthFlowContext(
              "member-user",
              "tas-user-id",
              "HKID",
              true,
              "reset-subject-id",
              null,
              null,
              null,
              null,
              accountEnv,
              trustCode,
              schemeName,
              schemeNameChi,
              "v1",
              ForgotPasswordProtectedPhase.IDENTITY_VERIFIED);
      vault.put("ct-0", initial);
      store.set(
          new AuthFlowSessionSnapshot(
              new AuthFlowSession(
                  SESSION_ID,
                  AuthFlowScenario.FORGOT_PASSWORD,
                  AuthFlowStatus.IDENTITY_VERIFIED,
                  "csrf-hash",
                  null,
                  new AuthFlowSessionData(
                      null,
                      null,
                      Map.of(
                          CIPHERTEXT_KEY, "ct-0",
                          "forgotPasswordContextKeyVersion", "kv-0",
                          "forgotPasswordContextAlgorithm", "AES_GCM_V1")),
                  NOW.minusSeconds(30),
                  NOW.minusSeconds(5),
                  NOW.plusSeconds(300)),
              new SessionRevision("0123456789abcdef0123456789abcdef")));

      AuthFlowSessionMutationLockPort locks = mock(AuthFlowSessionMutationLockPort.class);
      when(locks.acquire(any(), any())).thenReturn(Mono.just(lock));
      when(locks.release(lock)).thenReturn(Mono.just(true));

      // In-memory session store: every revisioned update replaces the stored snapshot.
      AuthFlowSessionPort sessions = mock(AuthFlowSessionPort.class);
      when(sessions.loadSnapshot(any()))
          .thenAnswer(invocation -> Mono.just(Optional.of(store.get())));
      when(sessions.updateRevisioned(any()))
          .thenAnswer(
              invocation -> {
                com.bct.ngtpa.apiservice.application.dto.session.authflow
                        .RevisionedAuthFlowSessionMutationCommand
                    command = invocation.getArgument(0);
                AuthFlowSessionSnapshot next =
                    new AuthFlowSessionSnapshot(
                        command.updated(),
                        new SessionRevision(String.format("%032x", revisionSeq.incrementAndGet())));
                store.set(next);
                return Mono.just(
                    new AuthFlowSessionMutationResult(
                        AuthFlowSessionMutationOutcome.APPLIED, next));
              });

      // Protector fake: keeps the exact context object that was protected, keyed by ciphertext.
      ForgotPasswordAuthFlowContextProtectorPort protector =
          mock(ForgotPasswordAuthFlowContextProtectorPort.class);
      when(protector.protect(any()))
          .thenAnswer(
              invocation -> {
                String ciphertext = "ct-" + contextSeq.incrementAndGet();
                vault.put(ciphertext, invocation.getArgument(0));
                return new ForgotPasswordAuthFlowEncryptedContext(ciphertext, "kv-1", "AES_GCM_V1");
              });
      when(protector.unprotect(any(ForgotPasswordAuthFlowEncryptedContext.class)))
          .thenAnswer(
              invocation -> {
                ForgotPasswordAuthFlowEncryptedContext encrypted = invocation.getArgument(0);
                return vault.get(encrypted.ciphertext());
              });

      ForgotPasswordDetailsLookupPort lookup = mock(ForgotPasswordDetailsLookupPort.class);
      when(lookup.verify(any())).thenReturn(Mono.just(lookupResult));

      Instant expiresAt = NOW.plusSeconds(300);
      OtpChallengePort otp = mock(OtpChallengePort.class);
      when(otp.createChallenge(any()))
          .thenReturn(Mono.just(new CreateOtpChallengeResult("otp-1", "123456", expiresAt, 300)));
      OtpChallengeStatePort challenges = mock(OtpChallengeStatePort.class);
      when(challenges.findById(any()))
          .thenReturn(
              Mono.just(
                  Optional.of(
                      new OtpChallenge(
                          "otp-1",
                          OtpScenario.FORGOT_PASSWORD,
                          SESSION_ID.value(),
                          OtpChannel.EMAIL,
                          "destinationhash",
                          "codehash",
                          OtpStatus.CREATED,
                          0,
                          5,
                          0,
                          5,
                          NOW.minusSeconds(1),
                          expiresAt,
                          NOW.minusSeconds(1),
                          null,
                          null))));

      SmsSenderPort smsSender =
          message ->
              Mono.just(new SenderResult(SenderStatus.ACCEPTED, "provider-sms-id", null, null));
      EmailSenderPort emailSender =
          message ->
              Mono.just(new SenderResult(SenderStatus.ACCEPTED, "provider-email-id", null, null));

      // REAL binding adapter over the in-memory store and the protector fake.
      ForgotPasswordOtpSessionBindingPort binding =
          new com.bct.ngtpa.apiservice.adapter.out.session.forgotpassword
              .ForgotPasswordOtpSessionBindingAdapter(sessions, challenges, protector);

      service =
          new ForgotPasswordDetailsOtpProtectedOperation(
              new SecureForgotPasswordOtpCommitRefGenerator(),
              locks,
              sessions,
              protector,
              new ForgotPasswordUnderLockDetailsVerifier(lookup),
              otp,
              new MessageDeliveryAdapter(smsSender, emailSender),
              new MaskingService(new MaskingProperties()),
              binding,
              new MemoryCommitStates(),
              new ForgotPasswordOtpCommitRetentionPolicy(
                  Duration.ofSeconds(60), Duration.ofMinutes(10)),
              new ForgotPasswordDetailsOtpLeasePolicy(60, 10, 10, 3, 10000, 100, 5000, 1000),
              clock,
              Duration.ofMinutes(5),
              new YamlNoticeTemplateRegistryAdapter(properties));
    }

    ForgotPasswordDetailsOtpResult execute(String email, String language) {
      return service
          .execute(
              new ForgotPasswordDetailsOtpCommand(
                  SESSION_ID,
                  "csrf",
                  "https://portal.example",
                  "https://portal.example/forgot-password",
                  "req-123",
                  language,
                  "ac2e9b54-836d-4729-8957-d89b1d8ae015",
                  "04/07/1978",
                  email,
                  null))
          .block()
          .result();
    }

    /** The protected context that the session store currently holds. */
    ForgotPasswordAuthFlowContext persistedContext() {
      String ciphertext = store.get().session().data().attributes().get(CIPHERTEXT_KEY);
      return vault.get(ciphertext);
    }

    private static final class MemoryCommitStates implements ForgotPasswordOtpCommitStatePort {
      private final Map<String, ForgotPasswordOtpCommitState> states = new java.util.HashMap<>();

      @Override
      public Mono<Boolean> saveIfAbsent(
          String opaqueCommitRef, ForgotPasswordOtpCommitState state, Duration ttl) {
        return Mono.just(states.putIfAbsent(opaqueCommitRef, state) == null);
      }

      @Override
      public Mono<Optional<ForgotPasswordOtpCommitState>> load(String opaqueCommitRef) {
        return Mono.just(Optional.ofNullable(states.get(opaqueCommitRef)));
      }

      @Override
      public Mono<Void> delete(String opaqueCommitRef) {
        states.remove(opaqueCommitRef);
        return Mono.empty();
      }
    }
  }
}
