package com.bct.ngtpa.apiservice.integration.registration;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationUsernameCheckWebMapper;
import com.bct.ngtpa.apiservice.application.dto.auth.RegistrationAuthWorkflowPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.UsernamePolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationEligibleTrustSet;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationSelectedTrust;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCreateCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionExpiryRefreshPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import com.bct.ngtpa.apiservice.application.port.out.DownstreamErrorClassificationPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationUsernameAvailabilityPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationUsernameCheckProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.CheckRegistrationUsernameCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.CheckRegistrationUsernameResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.CheckRegistrationUsernameResultStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.CheckRegistrationUsernameService;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.RegistrationUsernameAvailabilityResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Sub-task 2b.2 STEP 1, Priority 1 item 4, task 4 (2026-10-28 decision):
 * CheckRegistrationUsernameService.java:306.
 *
 * <p><b>Part 1</b> characterizes what the client actually receives for the service's {@code
 * downstreamFailed()} result - the REAL service (only the availability port stubbed) -> the REAL
 * {@link RegistrationUsernameCheckWebMapper} -> rendered JSON.
 *
 * <p><b>Part 2</b> drives the availability port's thrown-exception path instead, confirming BOTH
 * real ERROR log lines ({@code CheckRegistrationUsernameService.java:224-226} and {@code :302})
 * fire in order via a real Logback {@link ListAppender} attached to the service's own logger.
 *
 * <p>{@code technicalDownstreamFailureResponseDoesNotUseBusinessFallback} (a separate existing
 * test) is intentionally NOT touched by this class.
 */
class CheckRegistrationUsernameServiceCharacterizationTest {

  private static final Instant NOW = Instant.parse("2026-10-27T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final String TRUST_CODE = "TRUST1";
  private static final String ACCOUNT_ENV = "ENV1";
  private static final String USERNAME = "validuser1";
  private static final String CSRF_TOKEN = "csrf-token-value";
  private static final String CORRELATION_ID = "corr-1";

  @Test
  void downstreamFailureRendersAsBusinessTypeWithDownstreamErrorCode() throws Exception {
    RegistrationUsernameAvailabilityPort availabilityPort =
        command -> Mono.just(RegistrationUsernameAvailabilityResult.downstreamFailed());

    var service = newService(availabilityPort);
    CheckRegistrationUsernameResult result = service.check(newCommand()).block();

    assertThat(result.status()).isEqualTo(CheckRegistrationUsernameResultStatus.DOWNSTREAM_FAILED);

    var mapper = new RegistrationUsernameCheckWebMapper();
    var response = mapper.toMutationResponse(result);

    assertThat(response.success()).isFalse();
    assertThat(response.errors()).hasSize(1);
    var error = response.errors().getFirst();

    // RECORDED ACTUAL RESULT (not assumed): RegistrationUsernameCheckWebMapper.apiError()
    // hardcodes the literal "BUSINESS" type, discarding the service's real
    // WorkflowErrorType.DOWNSTREAM_SYSTEM entirely (confirmed from its complete real source).
    assertThat(error.type()).isEqualTo("BUSINESS");
    // The error CODE, unlike type, IS preserved through to the client.
    assertThat(error.code()).isEqualTo(SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE.value());
  }

  @Test
  void onErrorResumeExceptionLogsBothErrorLinesInOrder() throws Exception {
    RegistrationUsernameAvailabilityPort throwingAvailabilityPort =
        command -> Mono.error(new IllegalStateException("downstream availability check failed"));

    var service = newService(throwingAvailabilityPort);

    Logger logger = (Logger) LoggerFactory.getLogger(CheckRegistrationUsernameService.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      CheckRegistrationUsernameResult result = service.check(newCommand()).block();

      assertThat(result.status())
          .isEqualTo(CheckRegistrationUsernameResultStatus.DOWNSTREAM_FAILED);

      // Both real ERROR lines fire, in order: (1) the onErrorResume catch-all
      // (CheckRegistrationUsernameService.java:224-226), then (2) downstreamFailed()'s own line
      // (:302) - confirmed from the real checkAvailability() method body.
      assertThat(appender.list).hasSize(2);
      assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.ERROR);
      assertThat(appender.list.get(0).getFormattedMessage())
          .isEqualTo(
              "event=registration.usernameCheck.failure severity=FATAL status=TECHNICAL_FAILURE"
                  + " correlationId="
                  + CORRELATION_ID);
      assertThat(appender.list.get(1).getLevel()).isEqualTo(Level.ERROR);
      assertThat(appender.list.get(1).getFormattedMessage())
          .isEqualTo(
              "event=registration.usernameCheck.failure severity=FATAL status=DOWNSTREAM_FAILURE");
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  // ───────── shared fixtures ─────────

  private static CheckRegistrationUsernameService newService(
      RegistrationUsernameAvailabilityPort availabilityPort) throws Exception {
    AuthFlowSession session = validSession();

    AuthFlowSessionPort sessionPort =
        new AuthFlowSessionPort() {
          @Override
          public Mono<AuthFlowSession> create(AuthFlowSessionCreateCommand command) {
            return Mono.error(new AssertionError("create() must not be called"));
          }

          @Override
          public Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId authFlowSessionId) {
            return Mono.just(Optional.of(session));
          }
        };
    AuthFlowSessionExpiryRefreshPort expiryRefreshPort = (id, now) -> Mono.just(session);
    AuthWorkflowPolicyPort policyPort =
        new AuthWorkflowPolicyPort() {
          @Override
          public RegistrationAuthWorkflowPolicy registrationPolicy() {
            return new RegistrationAuthWorkflowPolicy(
                null, null, null, new UsernamePolicy(3, 20, true, true, false, false, null), null);
          }

          @Override
          public Optional<String> resolveIdentityTypeSearchScope(String identityType) {
            return Optional.empty();
          }
        };
    DownstreamErrorClassificationPort classificationPort =
        request -> {
          throw new AssertionError("classify() must not be called for this scenario");
        };
    RegistrationUsernameCheckProtectorPort protectorPort =
        new RegistrationUsernameCheckProtectorPort() {
          @Override
          public com.bct.ngtpa.apiservice.application.dto.session.authflow
                  .RegistrationUsernameCheckEncryptedState
              protect(
                  com.bct.ngtpa.apiservice.application.dto.session.authflow.UsernameCheckState
                      usernameCheckState) {
            throw new AssertionError("protect() must not be called for this scenario");
          }

          @Override
          public com.bct.ngtpa.apiservice.application.dto.session.authflow.UsernameCheckState
              unprotect(
                  com.bct.ngtpa.apiservice.application.dto.session.authflow
                          .RegistrationUsernameCheckEncryptedState
                      encryptedState) {
            throw new AssertionError("unprotect() must not be called for this scenario");
          }
        };

    return new CheckRegistrationUsernameService(
        sessionPort,
        expiryRefreshPort,
        availabilityPort,
        policyPort,
        classificationPort,
        protectorPort,
        CLOCK);
  }

  private static CheckRegistrationUsernameCommand newCommand() {
    return new CheckRegistrationUsernameCommand(
        validAuthFlowSessionId(),
        CSRF_TOKEN,
        new RegistrationSelectedTrust(TRUST_CODE, ACCOUNT_ENV),
        USERNAME,
        null,
        null,
        "en",
        CORRELATION_ID);
  }

  private static AuthFlowSession validSession() throws Exception {
    String csrfTokenHash = sha256Hex(CSRF_TOKEN);
    String encodedTrustAttribute = encodeField(ACCOUNT_ENV) + "." + encodeField(TRUST_CODE);
    return new AuthFlowSession(
        validAuthFlowSessionId(),
        AuthFlowScenario.REGISTRATION,
        AuthFlowStatus.TRUST_SELECTION_READY,
        csrfTokenHash,
        null,
        new AuthFlowSessionData(
            null, null, Map.of(RegistrationEligibleTrustSet.ATTRIBUTE_NAME, encodedTrustAttribute)),
        NOW.minusSeconds(60),
        NOW.minusSeconds(60),
        NOW.plusSeconds(600));
  }

  // Replicates RegistrationEligibleTrustSet's own private encodeField(...) algorithm exactly
  // (confirmed from its real source), avoiding the need for the unconfirmed
  // RegistrationLookupAccount class that encode() itself requires.
  private static String encodeField(String value) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  // Replicates CheckRegistrationUsernameService's own private sha256(...) algorithm exactly
  // (confirmed from its real source), to guarantee csrfMatches() succeeds.
  private static String sha256Hex(String value) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
  }

  // Mirrors the exact real, already-committed pattern in
  // P7RegistrationOtpIntegrationTest.validAuthFlowSessionId() - tries multiple candidate ID
  // formats and uses whichever one currently satisfies AuthFlowSessionId's real validation rules,
  // rather than guessing a single hardcoded format.
  private static AuthFlowSessionId validAuthFlowSessionId() {
    for (String candidate :
        List.of(
            "sid_0123456789abcdef",
            "sid_0123456789abcdef0123456789abcdef",
            "afs_0123456789abcdef",
            "afs_0123456789abcdef0123456789abcdef",
            "01234567-89ab-cdef-0123-456789abcdef",
            "opaqueAuthFlowSessionId0123456789")) {
      try {
        return new AuthFlowSessionId(candidate);
      } catch (RuntimeException ignored) {
        // Try the next candidate; this helper follows current value-object rules.
      }
    }
    throw new AssertionError(
        "No valid AuthFlowSessionId test fixture candidate matched current validation rules");
  }
}
