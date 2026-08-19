package com.bct.ngtpa.apiservice.integration.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthRegistrationController;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequiredRequestIdWebFilter;
import com.bct.ngtpa.apiservice.application.dto.auth.CaptchaPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.PreStartChallengePolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.PreStartChallengeType;
import com.bct.ngtpa.apiservice.application.dto.auth.RegistrationAuthWorkflowPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.*;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.*;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.*;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.*;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.port.in.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.service.auth.captcha.RegistrationCaptchaGateService;
import com.bct.ngtpa.apiservice.application.service.auth.idempotency.Sha256RegistrationRequestHashGenerator;
import com.bct.ngtpa.apiservice.application.service.auth.registration.RegistrationStartValidator;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.RegistrationOtpCreationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationService;
import com.bct.ngtpa.apiservice.infrastructure.security.CorsProperties;
import com.bct.ngtpa.apiservice.infrastructure.security.SecurityConfig;
import com.bct.ngtpa.apiservice.integration.registration.support.RegistrationTestCase;
import com.bct.ngtpa.apiservice.integration.registration.support.RegistrationTestResultExtension;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = RegistrationStartWorkflowIntegrationTest.TestApplication.class,
    properties = {
      "api.security.require-authentication=false",
      "redis-cache.enabled=false",
      "spring.main.banner-mode=off"
    })
@AutoConfigureWebTestClient
@ExtendWith(RegistrationTestResultExtension.class)
class RegistrationStartWorkflowIntegrationTest {
  private static final String START = "/api/v1/auth/registration/start";
  private static final Instant NOW = Instant.parse("2026-07-17T00:00:00Z");

  @Autowired WebTestClient client;
  @Autowired InMemoryCaptchaPort captcha;
  @Autowired StatefulIdempotencyPort idempotency;
  @Autowired RecordingLookupPort lookup;
  @Autowired RecordingSessionPort sessions;
  @Autowired RecordingOtpPort otp;
  @Autowired RecordingMessagePort messages;
  @Autowired SequenceState sequence;
  @Autowired ReplayState replayState;

  @BeforeEach
  void reset() {
    captcha.clear();
    idempotency.clear();
    lookup.reset();
    sessions.reset();
    otp.reset();
    messages.reset();
    sequence.reset();
    replayState.clear();
  }

  @Test
  @RegistrationTestCase("TC-013")
  void eligibleEmailStartsRealWorkflow() {
    String challenge = verifiedCaptcha();
    start("email-success", body("GPFS", "1234567890", "member@example.com", null, challenge))
        .expectStatus()
        .isOk()
        .expectHeader()
        .value(
            "Set-Cookie",
            value ->
                assertThat(value)
                    .contains("auth_flow_sid=sid-1")
                    .contains("Secure")
                    .containsIgnoringCase("HttpOnly")
                    .contains("SameSite=None"))
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(true)
        .jsonPath("$.result.nextStep")
        .isEqualTo("VERIFY_OTP")
        .jsonPath("$.result.data.remainingResendAttempts")
        .isEqualTo(5)
        .jsonPath("$.result.data.csrfToken")
        .isNotEmpty()
        .jsonPath("$.result.data.maskedDestination")
        .isEqualTo("m****r@example.com");
    assertThat(lookup.calls).isEqualTo(1);
    assertThat(otp.destinations).containsExactly("member@example.com");
    assertThat(messages.recipients).containsExactly("member@example.com");
  }

  @Test
  @RegistrationTestCase("TC-014")
  void eligibleMobileStartsRealWorkflow() {
    String challenge = verifiedCaptcha();
    start("mobile-success", body("GPFS", "1234567890", null, "91234567", challenge))
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(true)
        .jsonPath("$.result.data.remainingResendAttempts")
        .isEqualTo(5);
    assertThat(otp.destinations).containsExactly("91234567");
    assertThat(messages.recipients).containsExactly("91234567");
  }

  @Test
  @RegistrationTestCase("TC-015")
  void missingCaptchaStopsAllDownstreamSideEffects() {
    start("missing-captcha", body("GPFS", "1234567890", "member@example.com", null, null))
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(false);
    assertNoOperationalSideEffects();
  }

  @Test
  @RegistrationTestCase("TC-016")
  void unverifiedCaptchaCannotBeBypassed() {
    String challenge = captcha.seed(CaptchaStatus.CREATED, false);
    start("unverified-captcha", body("GPFS", "1234567890", "member@example.com", null, challenge))
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.errors[0].code")
        .isEqualTo("err.registration.captcha.invalid");
    assertNoOperationalSideEffects();
  }

  @Test
  @RegistrationTestCase("TC-017")
  void captchaIsConsumedOnce() {
    String challenge = verifiedCaptcha();
    start("consume-1", body("GPFS", "1234567890", "member@example.com", null, challenge))
        .expectStatus()
        .isOk();
    int lookupAfterFirst = lookup.calls;
    int otpAfterFirst = otp.destinations.size();
    start("consume-2", body("GPFS", "1234567890", "member@example.com", null, challenge))
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.errors[0].code")
        .isEqualTo("err.registration.captcha.consumed");
    assertThat(lookup.calls).isEqualTo(lookupAfterFirst);
    assertThat(otp.destinations).hasSize(otpAfterFirst);
  }

  @Test
  @RegistrationTestCase("TC-018")
  void missingIdempotencyKeyStopsWorkflow() {
    String challenge = verifiedCaptcha();
    client
        .post()
        .uri(START)
        .header("X-Request-Id", requestId())
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body("GPFS", "1234567890", "member@example.com", null, challenge))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.errors[0].code")
        .isEqualTo("err.workflow.idempotency.missing");
    assertNoOperationalSideEffects();
  }

  @Test
  @RegistrationTestCase("TC-019")
  void sameKeyAndRequestReplayCurrentSessionWithoutDuplicateSideEffects() {
    String challenge = verifiedCaptcha();
    Map<String, Object> request = body("GPFS", "1234567890", "member@example.com", null, challenge);

    startWithKey("replay-key", request)
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.result.nextStep")
        .isEqualTo("VERIFY_OTP")
        .jsonPath("$.result.data.remainingResendAttempts")
        .isEqualTo(5);

    int lookupCalls = lookup.calls;
    int sessionCreates = sessions.created;
    int otpCreates = otp.destinations.size();
    int deliveries = messages.recipients.size();

    client
        .post()
        .uri(START)
        .header("X-Request-Id", requestId())
        .header("Idempotency-Key", "replay-key")
        .header("Accept-Language", "en")
        .cookie("auth_flow_sid", "sid-1")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(true)
        .jsonPath("$.result.nextStep")
        .isEqualTo("VERIFY_OTP")
        .jsonPath("$.result.data.remainingResendAttempts")
        .isEqualTo(5)
        .jsonPath("$.result.data.csrfToken")
        .isEqualTo("csrf-safe-token");

    assertThat(lookup.calls).isEqualTo(lookupCalls);
    assertThat(sessions.created).isEqualTo(sessionCreates);
    assertThat(otp.destinations).hasSize(otpCreates);
    assertThat(messages.recipients).hasSize(deliveries);
  }

  @Test
  @RegistrationTestCase("TC-020")
  void reusedKeyWithDifferentRequestIsConflictWithoutNewSideEffects() {
    String firstCaptcha = verifiedCaptcha();
    startWithKey("same-key", body("GPFS", "1234567890", "member@example.com", null, firstCaptcha))
        .expectStatus()
        .isOk();
    int calls = lookup.calls;
    String secondCaptcha = verifiedCaptcha();
    startWithKey("same-key", body("GPFS", "9999999999", "other@example.com", null, secondCaptcha))
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.errors[0].code")
        .isEqualTo("err.workflow.idempotency.keyReused");
    assertThat(lookup.calls).isEqualTo(calls);
  }

  @Test
  @RegistrationTestCase("TC-021")
  void exactlyOneContactIsRequired() {
    String first = verifiedCaptcha();
    start("no-contact", body("GPFS", "1234567890", null, null, first))
        .expectStatus()
        .isBadRequest();
    String second = verifiedCaptcha();
    start("two-contacts", body("GPFS", "1234567890", "member@example.com", "91234567", second))
        .expectStatus()
        .isBadRequest();
    assertNoOperationalSideEffects();
  }

  @Test
  @RegistrationTestCase("TC-022")
  void invalidIdentityDateAndContactFormatsNeverCallLookup() {
    start("bad-format", body("BAD", "x", "not-email", null, verifiedCaptcha()))
        .expectStatus()
        .isBadRequest();
    assertNoOperationalSideEffects();
  }

  @Test
  @RegistrationTestCase("TC-024")
  void eligibleLookupAccountDataIsNotExposed() {
    String challenge = verifiedCaptcha();
    start("eligible", body("GPFS", "1234567890", "member@example.com", null, challenge))
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .value(value -> assertThat(value).doesNotContain("OI", "JPM", "49998888"));
  }

  @Test
  @RegistrationTestCase("TC-025")
  void memberNotFoundReturnsCanonicalVerificationFailure() {
    lookup.result =
        new RegistrationLookupResult(
            RegistrationLookupStatus.NOT_ELIGIBLE, List.of(), "err.tpa.member.not.found");
    start("not-found", body("GPFS", "1234567890", "member@example.com", null, verifiedCaptcha()))
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.errors[0].code")
        .isEqualTo("err.registration.start.verificationFailed");
    assertThat(sessions.created).isZero();
    assertThat(otp.destinations).isEmpty();
  }

  @Test
  @RegistrationTestCase("TC-031")
  void repeatedDistinctStartsHaveNoRegistrationStartRateLimit() {
    for (int i = 0; i < 8; i++) {
      start(
              "many-" + i,
              body("GPFS", "123456789" + i, "member@example.com", null, verifiedCaptcha()))
          .expectStatus()
          .isOk();
    }
    assertThat(lookup.calls).isEqualTo(8);
    assertThat(messages.recipients).hasSize(8);
  }

  @Test
  @RegistrationTestCase("TC-032")
  void messageDeliveryFailureIsSafeAndRecordedFailed() {
    messages.accepted = false;
    start(
            "delivery-failure",
            body("GPFS", "1234567890", "member@example.com", null, verifiedCaptcha()))
        .expectStatus()
        .isEqualTo(502)
        .expectBody(String.class)
        .value(
            value ->
                assertThat(value)
                    .contains("err.registration.message.deliveryFailed")
                    .doesNotContain("member@example.com", "123456"));
    assertThat(idempotency.failed).isEqualTo(1);
  }

  private WebTestClient.ResponseSpec start(String suffix, Map<String, Object> body) {
    return startWithKey("idem-" + suffix, body);
  }

  private WebTestClient.ResponseSpec startWithKey(String key, Map<String, Object> body) {
    return client
        .post()
        .uri(START)
        .header("X-Request-Id", requestId())
        .header("Idempotency-Key", key)
        .header("Accept-Language", "en")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .exchange();
  }

  private Map<String, Object> body(
      String identityType, String identityValue, String email, String mobile, String captchaId) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("identityType", identityType);
    value.put("identityValue", identityValue);
    value.put("dateOfBirth", "31/01/1980");
    value.put("email", email);
    value.put("mobile", mobile);
    value.put("captchaChallengeId", captchaId);
    return value;
  }

  private String verifiedCaptcha() {
    return captcha.seed(CaptchaStatus.VERIFIED, false);
  }

  private String requestId() {
    return "0041-start-" + sequence.next();
  }

  private void assertNoOperationalSideEffects() {
    assertThat(lookup.calls).isZero();
    assertThat(sessions.created).isZero();
    assertThat(otp.destinations).isEmpty();
    assertThat(messages.recipients).isEmpty();
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      excludeName =
          "org.springframework.boot.micrometer.metrics.autoconfigure.ssl.SslMetricsAutoConfiguration")
  @EnableConfigurationProperties(CorsProperties.class)
  @Import(SecurityConfig.class)
  static class TestApplication {
    @Bean
    Clock clock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    @Bean
    SequenceState sequence() {
      return new SequenceState();
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    ErrorMessageResolver messages() {
      return (code, language, env, trust, scheme) -> code;
    }

    @Bean
    RequiredRequestIdWebFilter requiredRequestIdWebFilter(ObjectMapper mapper) {
      return new RequiredRequestIdWebFilter(mapper);
    }

    @Bean
    InMemoryCaptchaPort captchaPort() {
      return new InMemoryCaptchaPort();
    }

    @Bean
    RegistrationCaptchaGateService captchaGate(InMemoryCaptchaPort port, Clock clock) {
      return new RegistrationCaptchaGateService(port, clock);
    }

    @Bean
    AuthWorkflowPolicyPort policy() {
      return new TestPolicy();
    }

    @Bean
    StatefulIdempotencyPort idempotency() {
      return new StatefulIdempotencyPort();
    }

    @Bean
    RecordingLookupPort lookup() {
      return new RecordingLookupPort();
    }

    @Bean
    ReplayState replayState() {
      return new ReplayState();
    }

    @Bean
    RecordingSessionPort sessions(ReplayState state) {
      return new RecordingSessionPort(state);
    }

    @Bean
    RegistrationCsrPort csrf() {
      return sid -> Mono.just("csrf-safe-token");
    }

    @Bean
    RecordingOtpPort otp(ReplayState state) {
      return new RecordingOtpPort(state);
    }

    @Bean
    RecordingMessagePort message() {
      return new RecordingMessagePort();
    }

    @Bean
    StartRegistrationUseCase startUseCase(
        StatefulIdempotencyPort idem,
        RecordingLookupPort lookup,
        RecordingSessionPort sessions,
        RegistrationCsrPort csrf,
        RecordingOtpPort otp,
        RecordingMessagePort message,
        AuthWorkflowPolicyPort policy,
        RegistrationCaptchaGateService gate,
        ReplayState replayState,
        Clock clock) {
      return new StartRegistrationService(
          new RegistrationStartValidator(),
          new Sha256RegistrationRequestHashGenerator(),
          idem,
          lookup,
          sessions,
          csrf,
          otp,
          message,
          replayState,
          replayState,
          policy,
          gate,
          clock);
    }

    @Bean
    RegistrationStartWebMapper startMapper(ErrorMessageResolver resolver) {
      return new RegistrationStartWebMapper(Duration.ofMinutes(5), resolver);
    }

    @Bean
    AuthFlowCookieWriter cookieWriter() {
      return AuthFlowCookieWriter.defaultWriter();
    }

    @Bean
    AuthRegistrationController controller(
        StartRegistrationUseCase start,
        RegistrationStartWebMapper mapper,
        AuthFlowCookieWriter writer) {
      return new AuthRegistrationController(start, mapper, writer, null, null, null, null, null);
    }
  }

  static final class SequenceState {
    private final AtomicInteger value = new AtomicInteger();

    int next() {
      return value.incrementAndGet();
    }

    void reset() {
      value.set(0);
    }
  }

  static final class TestPolicy implements AuthWorkflowPolicyPort {
    public RegistrationAuthWorkflowPolicy registrationPolicy() {
      return new RegistrationAuthWorkflowPolicy(
          new PreStartChallengePolicy(true, PreStartChallengeType.CAPTCHA),
          new CaptchaPolicy(8, "ABC123", false, 300, 5),
          null,
          null,
          null);
    }

    public Optional<String> resolveIdentityTypeSearchScope(String identityType) {
      return Optional.of("STAFF_NO".equals(identityType) ? "STAFFNO" : identityType);
    }
  }

  static final class InMemoryCaptchaPort implements CaptchaChallengePort {
    private final Map<CaptchaChallengeId, CaptchaChallenge> values = new ConcurrentHashMap<>();
    private final AtomicInteger ids = new AtomicInteger();

    String seed(CaptchaStatus status, boolean expired) {
      CaptchaChallengeId id =
          new CaptchaChallengeId("cap_" + String.format("%016d", ids.incrementAndGet()));
      CaptchaChallenge challenge =
          new CaptchaChallenge(
              id,
              new CaptchaAnswerHash("0".repeat(64)),
              status,
              0,
              5,
              NOW,
              expired ? NOW.minusSeconds(1) : NOW.plusSeconds(300),
              status == CaptchaStatus.VERIFIED ? NOW : null,
              null,
              null,
              null);
      values.put(id, challenge);
      return id.value();
    }

    void clear() {
      values.clear();
      ids.set(0);
    }

    public Mono<CaptchaChallenge> create(CaptchaChallenge value) {
      values.put(value.captchaChallengeId(), value);
      return Mono.just(value);
    }

    public Mono<Optional<CaptchaChallenge>> load(CaptchaChallengeId id) {
      return Mono.just(Optional.ofNullable(values.get(id)));
    }

    public Mono<CaptchaChallenge> markVerified(CaptchaChallengeId id, Instant at) {
      return Mono.just(values.get(id));
    }

    public Mono<CaptchaChallenge> incrementFailedAttempt(CaptchaChallengeId id, Instant at) {
      return Mono.just(values.get(id));
    }

    public Mono<CaptchaChallenge> markLocked(CaptchaChallengeId id, Instant at) {
      return Mono.just(values.get(id));
    }

    public Mono<CaptchaChallenge> markConsumed(CaptchaChallengeId id, Instant at) {
      CaptchaChallenge o = values.get(id);
      CaptchaChallenge n =
          new CaptchaChallenge(
              o.captchaChallengeId(),
              o.answerHash(),
              CaptchaStatus.CONSUMED,
              o.failedAttemptCount(),
              o.maxAttempts(),
              o.createdAt(),
              o.expiresAt(),
              o.verifiedAt(),
              at,
              o.refreshedAt(),
              o.lockedAt());
      values.put(id, n);
      return Mono.just(n);
    }

    public Mono<CaptchaChallenge> markRefreshed(CaptchaChallengeId id, Instant at) {
      return Mono.just(values.get(id));
    }

    public Mono<Void> delete(CaptchaChallengeId id) {
      values.remove(id);
      return Mono.empty();
    }
  }

  static final class StatefulIdempotencyPort implements RegistrationIdempotencyPort {
    private final Map<String, RegistrationIdempotencyRecord> records = new ConcurrentHashMap<>();
    int failed;

    public Mono<RegistrationIdempotencyStartResult> startProcessing(
        RegistrationIdempotencyStartCommand c) {
      String key = c.idempotencyKey().value();
      RegistrationIdempotencyRecord existing = records.get(key);
      if (existing != null) {
        RegistrationIdempotencyStartOutcome outcome =
            existing.requestHash().equals(c.requestHash())
                ? RegistrationIdempotencyStartOutcome.COMPLETED_REPLAY
                : RegistrationIdempotencyStartOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST;
        return Mono.just(new RegistrationIdempotencyStartResult(outcome, existing));
      }
      RegistrationIdempotencyRecord created =
          new RegistrationIdempotencyRecord(
              c.action(),
              c.idempotencyKey(),
              c.requestHash(),
              RegistrationIdempotencyStatus.PROCESSING,
              null,
              c.now(),
              c.now(),
              c.expiresAt());
      records.put(key, created);
      return Mono.just(
          new RegistrationIdempotencyStartResult(
              RegistrationIdempotencyStartOutcome.STARTED, created));
    }

    public Mono<RegistrationIdempotencyRecord> markCompleted(
        RegistrationIdempotencyCompleteCommand c) {
      RegistrationIdempotencyRecord r =
          new RegistrationIdempotencyRecord(
              c.action(),
              c.idempotencyKey(),
              c.requestHash(),
              RegistrationIdempotencyStatus.COMPLETED,
              c.responseSnapshot(),
              c.completedAt(),
              c.completedAt(),
              c.completedAt().plus(c.ttl()));
      records.put(c.idempotencyKey().value(), r);
      return Mono.just(r);
    }

    public Mono<RegistrationIdempotencyRecord> markFailed(RegistrationIdempotencyFailCommand c) {
      failed++;
      RegistrationIdempotencyRecord r = records.get(c.idempotencyKey().value());
      return Mono.just(r);
    }

    public Mono<Optional<RegistrationIdempotencyRecord>> load(
        RegistrationIdempotencyAction a, RegistrationIdempotencyKey k) {
      return Mono.just(Optional.ofNullable(records.get(k.value())));
    }

    public Mono<Void> delete(RegistrationIdempotencyAction a, RegistrationIdempotencyKey k) {
      records.remove(k.value());
      return Mono.empty();
    }

    void clear() {
      records.clear();
      failed = 0;
    }
  }

  static final class RecordingLookupPort implements RegistrationLookupPort {
    int calls;
    RegistrationLookupResult result;

    RecordingLookupPort() {
      reset();
    }

    public Mono<RegistrationLookupResult> searchUnregisteredMember(
        RegistrationLookupCommand command) {
      calls++;
      return Mono.just(result);
    }

    void reset() {
      calls = 0;
      result =
          new RegistrationLookupResult(
              RegistrationLookupStatus.ELIGIBLE,
              List.of(new RegistrationLookupAccount("member@example.com", "91234567", "OI", "JPM")),
              null);
    }
  }

  static final class RecordingSessionPort implements RegistrationSessionPort {
    private final ReplayState state;
    int created;

    RecordingSessionPort(ReplayState state) {
      this.state = state;
    }

    public Mono<String> createRegistrationSession(String masked, RegistrationContactType type) {
      created++;
      String sid = "sid-" + created;
      state.createSession(sid, type, masked);
      return Mono.just(sid);
    }

    public Mono<Void> updateOtpChallenge(
        String sid, String otpId, RegistrationContactType type, String masked) {
      state.attachOtp(sid, otpId, type, masked);
      return Mono.empty();
    }

    void reset() {
      created = 0;
    }
  }

  static final class RecordingOtpPort implements RegistrationOtpPort {
    private final ReplayState state;
    final List<String> destinations = new ArrayList<>();

    RecordingOtpPort(ReplayState state) {
      this.state = state;
    }

    public Mono<RegistrationOtpCreationResult> createOtp(
        RegistrationContactType type, String destination) {
      destinations.add(destination);
      String id = "otp-" + destinations.size();
      state.saveOtp(id, type);
      return Mono.just(new RegistrationOtpCreationResult(id, "123456", 300, 60, 5));
    }

    void reset() {
      destinations.clear();
    }
  }

  static final class ReplayState implements AuthFlowSessionPort, OtpChallengeStatePort {
    private final Map<AuthFlowSessionId, AuthFlowSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, OtpChallenge> challenges = new ConcurrentHashMap<>();

    void createSession(String sid, RegistrationContactType type, String masked) {
      AuthFlowSessionId id = new AuthFlowSessionId(sid);
      sessions.put(
          id,
          new AuthFlowSession(
              id,
              AuthFlowScenario.REGISTRATION,
              AuthFlowStatus.OTP_SENT,
              "csrf-hash",
              null,
              new AuthFlowSessionData(type.name(), masked, Map.of()),
              NOW,
              NOW,
              NOW.plusSeconds(1800)));
    }

    void attachOtp(String sid, String otpId, RegistrationContactType type, String masked) {
      AuthFlowSessionId id = new AuthFlowSessionId(sid);
      AuthFlowSession old = sessions.get(id);
      sessions.put(
          id,
          new AuthFlowSession(
              id,
              old.scenario(),
              AuthFlowStatus.OTP_SENT,
              old.csrfTokenHash(),
              otpId,
              new AuthFlowSessionData(type.name(), masked, Map.of()),
              old.createdAt(),
              NOW,
              old.expiresAt()));
    }

    void saveOtp(String otpId, RegistrationContactType type) {
      challenges.put(
          otpId,
          new OtpChallenge(
              otpId,
              OtpScenario.REGISTRATION,
              "sid-1",
              type == RegistrationContactType.EMAIL ? OtpChannel.EMAIL : OtpChannel.SMS,
              "destination-hash",
              "code-hash",
              OtpStatus.CREATED,
              0,
              5,
              0,
              5,
              NOW,
              NOW.plusSeconds(300),
              NOW,
              null,
              null));
    }

    public Mono<AuthFlowSession> create(AuthFlowSessionCreateCommand command) {
      AuthFlowSession value =
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
      sessions.put(value.authFlowSessionId(), value);
      return Mono.just(value);
    }

    public Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId id) {
      return Mono.just(Optional.ofNullable(sessions.get(id)));
    }

    public Mono<AuthFlowSession> update(AuthFlowSession value) {
      sessions.put(value.authFlowSessionId(), value);
      return Mono.just(value);
    }

    public Mono<AuthFlowSession> touch(AuthFlowSessionId id, Instant now) {
      return load(id).map(Optional::orElseThrow);
    }

    public Mono<AuthFlowSession> complete(AuthFlowSessionId id, Instant now) {
      return load(id).map(Optional::orElseThrow);
    }

    public Mono<AuthFlowSession> cancel(AuthFlowSessionId id, Instant now) {
      return load(id).map(Optional::orElseThrow);
    }

    public Mono<Void> delete(AuthFlowSessionId id) {
      sessions.remove(id);
      return Mono.empty();
    }

    public Mono<Void> save(OtpChallenge challenge) {
      challenges.put(challenge.otpChallengeId(), challenge);
      return Mono.empty();
    }

    public Mono<Optional<OtpChallenge>> findById(String id) {
      return Mono.just(Optional.ofNullable(challenges.get(id)));
    }

    public Mono<Void> update(OtpChallenge challenge) {
      challenges.put(challenge.otpChallengeId(), challenge);
      return Mono.empty();
    }

    public Mono<Void> delete(String id) {
      challenges.remove(id);
      return Mono.empty();
    }

    void clear() {
      sessions.clear();
      challenges.clear();
    }
  }

  static final class RecordingMessagePort implements RegistrationMessagePort {
    final List<String> recipients = new ArrayList<>();
    boolean accepted = true;

    public Mono<Boolean> deliverRegistrationOtp(
        RegistrationContactType type,
        String recipient,
        String code,
        int ttl,
        String lang,
        String correlation) {
      recipients.add(recipient);
      return Mono.just(accepted);
    }

    void reset() {
      recipients.clear();
      accepted = true;
    }
  }
}
