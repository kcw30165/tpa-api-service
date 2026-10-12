package com.bct.ngtpa.apiservice.application.usecase.auth.registration.username;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.RegistrationAuthWorkflowPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.UsernamePolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationEligibleTrustSet;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupAccount;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationSelectedTrust;
import com.bct.ngtpa.apiservice.application.dto.downstream.DownstreamErrorClassification;
import com.bct.ngtpa.apiservice.application.dto.downstream.DownstreamErrorClassificationRequest;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCreateCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationOutcome;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationResult;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.RegistrationUsernameCheckAttributeNames;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.RegistrationUsernameCheckEncryptedState;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.RevisionedAuthFlowSessionMutationCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SelectedTrust;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SessionRevision;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.UsernameCheckState;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionExpiryRefreshPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import com.bct.ngtpa.apiservice.application.port.out.DownstreamErrorClassificationPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationUsernameAvailabilityPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationUsernameCheckProtectorPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CheckRegistrationUsernameServiceTest {
  private static final Instant NOW = Instant.parse("2026-07-10T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Test
  void rejectsMissingAuthFlowAndDoesNotCallAvailability() {
    var sessions = FakeSessions.empty();
    var availability = new FakeAvailability(RegistrationUsernameAvailabilityResult.available());
    var service =
        service(
            sessions, availability, new FakeClassifier("err.registration.username.isRegistered"));

    StepVerifier.create(service.check(command("csrf", selectedTrust("TL", "ENV1"), "abc1@")))
        .assertNext(
            result ->
                assertThat(result.status())
                    .isEqualTo(CheckRegistrationUsernameResultStatus.INVALID_AUTH_FLOW))
        .verifyComplete();

    assertThat(availability.calls).isZero();
    assertThat(sessions.updated).isNull();
  }

  @Test
  void rejectsInvalidCsrfAndDoesNotCallAvailability() {
    var sessions = FakeSessions.with(session(AuthFlowStatus.OTP_VERIFIED, "correct-csrf", null));
    var availability = new FakeAvailability(RegistrationUsernameAvailabilityResult.available());
    var service =
        service(
            sessions, availability, new FakeClassifier("err.registration.username.isRegistered"));

    StepVerifier.create(service.check(command("wrong-csrf", selectedTrust("TL", "ENV1"), "abc1@")))
        .assertNext(
            result ->
                assertThat(result.status())
                    .isEqualTo(CheckRegistrationUsernameResultStatus.CSRF_INVALID))
        .verifyComplete();

    assertThat(availability.calls).isZero();
    assertThat(sessions.updated).isNull();
  }

  @Test
  void rejectsInvalidStateAndDoesNotCallAvailability() {
    var sessions = FakeSessions.with(session(AuthFlowStatus.OTP_SENT, "csrf", null));
    var availability = new FakeAvailability(RegistrationUsernameAvailabilityResult.available());
    var service =
        service(
            sessions, availability, new FakeClassifier("err.registration.username.isRegistered"));

    StepVerifier.create(service.check(command("csrf", selectedTrust("TL", "ENV1"), "abc1@")))
        .assertNext(
            result -> {
              assertThat(result.status())
                  .isEqualTo(CheckRegistrationUsernameResultStatus.INVALID_STATE);
              assertThat(result.nextStep()).isEqualTo(WorkflowNextStep.USERNAME_CHECK);
            })
        .verifyComplete();

    assertThat(availability.calls).isZero();
  }

  @Test
  void acceptsOtpVerifiedStoresUsernameCheckAndTransitionsToUsernameVerified() {
    var sessions = FakeSessions.with(session(AuthFlowStatus.OTP_VERIFIED, "csrf", null));
    var availability = new FakeAvailability(RegistrationUsernameAvailabilityResult.available());
    var service =
        service(
            sessions, availability, new FakeClassifier("err.registration.username.isRegistered"));

    StepVerifier.create(service.check(command("csrf", selectedTrust("TL", "ENV1"), "abc1@")))
        .assertNext(
            result -> {
              assertThat(result.status())
                  .isEqualTo(CheckRegistrationUsernameResultStatus.USERNAME_AVAILABLE);
              assertThat(result.nextStep()).isEqualTo(WorkflowNextStep.PASSWORD_SETUP);
              assertThat(result.errors()).isEmpty();
            })
        .verifyComplete();

    assertThat(availability.calls).isOne();
    assertThat(sessions.updated.status()).isEqualTo(AuthFlowStatus.USERNAME_VERIFIED);
    assertThat(sessions.updated.data().attributes())
        .containsKeys(
            RegistrationUsernameCheckAttributeNames.USERNAME_CHECK_CIPHERTEXT,
            RegistrationUsernameCheckAttributeNames.USERNAME_CHECK_KEY_VERSION,
            RegistrationUsernameCheckAttributeNames.USERNAME_CHECK_ALGORITHM);
    assertThat(sessions.updated.data().attributes().toString())
        .doesNotContain("abc1@", "TL", "ENV1");
  }

  @Test
  void acceptsTrustSelectionReadyStoresUsernameCheckAndTransitionsToUsernameVerified() {
    var sessions = FakeSessions.with(session(AuthFlowStatus.TRUST_SELECTION_READY, "csrf", null));
    var availability = new FakeAvailability(RegistrationUsernameAvailabilityResult.available());
    var service =
        service(
            sessions, availability, new FakeClassifier("err.registration.username.isRegistered"));

    StepVerifier.create(service.check(command("csrf", selectedTrust("TL", "ENV1"), "abc1@")))
        .assertNext(
            result ->
                assertThat(result.status())
                    .isEqualTo(CheckRegistrationUsernameResultStatus.USERNAME_AVAILABLE))
        .verifyComplete();

    assertThat(sessions.updated.status()).isEqualTo(AuthFlowStatus.USERNAME_VERIFIED);
    assertThat(sessions.updated.data().attributes().toString()).doesNotContain("abc1@");
  }

  @Test
  void rejectsSelectedTrustMissingOrBlankBeforeAvailability() {
    var sessions = FakeSessions.with(session(AuthFlowStatus.OTP_VERIFIED, "csrf", null));
    var availability = new FakeAvailability(RegistrationUsernameAvailabilityResult.available());
    var service =
        service(
            sessions, availability, new FakeClassifier("err.registration.username.isRegistered"));

    StepVerifier.create(service.check(command("csrf", null, "abc1@")))
        .assertNext(
            result -> {
              assertThat(result.status())
                  .isEqualTo(CheckRegistrationUsernameResultStatus.VALIDATION_FAILED);
              assertThat(result.errors().getFirst().code())
                  .isEqualTo(RegistrationErrorKey.TRUST_SELECTION_INVALID.value());
            })
        .verifyComplete();

    StepVerifier.create(service.check(command("csrf", selectedTrust(" ", "ENV1"), "abc1@")))
        .assertNext(
            result -> {
              assertThat(result.status())
                  .isEqualTo(CheckRegistrationUsernameResultStatus.VALIDATION_FAILED);
              assertThat(result.errors().getFirst().code())
                  .isEqualTo(RegistrationErrorKey.TRUST_SELECTION_INVALID.value());
            })
        .verifyComplete();

    assertThat(availability.calls).isZero();
  }

  @Test
  void rejectsUsernamePolicyViolationBeforeAvailability() {
    var sessions = FakeSessions.with(session(AuthFlowStatus.OTP_VERIFIED, "csrf", null));
    var availability = new FakeAvailability(RegistrationUsernameAvailabilityResult.available());
    var service =
        service(
            sessions, availability, new FakeClassifier("err.registration.username.isRegistered"));

    StepVerifier.create(service.check(command("csrf", selectedTrust("TL", "ENV1"), "abcd")))
        .assertNext(
            result -> {
              assertThat(result.status())
                  .isEqualTo(CheckRegistrationUsernameResultStatus.VALIDATION_FAILED);
              assertThat(result.errors().getFirst().code())
                  .isEqualTo(RegistrationErrorKey.USERNAME_POLICY_INVALID.value());
            })
        .verifyComplete();

    assertThat(availability.calls).isZero();
  }

  @Test
  void callsAvailabilityWithNormalizedSelectedTrustAndUsername() {
    var sessions = FakeSessions.with(session(AuthFlowStatus.OTP_VERIFIED, "csrf", null));
    var availability = new FakeAvailability(RegistrationUsernameAvailabilityResult.available());
    var service =
        service(
            sessions, availability, new FakeClassifier("err.registration.username.isRegistered"));

    StepVerifier.create(service.check(command("csrf", selectedTrust(" TL ", " ENV1 "), " abc1@ ")))
        .assertNext(
            result ->
                assertThat(result.status())
                    .isEqualTo(CheckRegistrationUsernameResultStatus.USERNAME_AVAILABLE))
        .verifyComplete();

    assertThat(availability.lastCommand.selectedTrust().trustCode()).isEqualTo("TL");
    assertThat(availability.lastCommand.selectedTrust().accountEnv()).isEqualTo("ENV1");
    assertThat(availability.lastCommand.username()).isEqualTo("abc1@");
  }

  @Test
  void mapsDownstreamBusinessRejectionThroughClassifierAndDoesNotStoreUsernameCheck() {
    var sessions = FakeSessions.with(session(AuthFlowStatus.OTP_VERIFIED, "csrf", null));
    var availability =
        new FakeAvailability(
            RegistrationUsernameAvailabilityResult.rejected("err.tpa.username.taken"));
    var classifier = new FakeClassifier("err.registration.username.isRegistered");
    var service = service(sessions, availability, classifier);

    StepVerifier.create(service.check(command("csrf", selectedTrust("TL", "ENV1"), "abc1@")))
        .assertNext(
            result -> {
              assertThat(result.status())
                  .isEqualTo(CheckRegistrationUsernameResultStatus.DOWNSTREAM_REJECTED);
              assertThat(result.nextStep()).isEqualTo(WorkflowNextStep.USERNAME_CHECK);
              assertThat(result.errors().getFirst().code())
                  .isEqualTo("err.registration.username.isRegistered");
            })
        .verifyComplete();

    assertThat(classifier.calls).isOne();
    assertThat(classifier.lastRequest.pageId()).isEqualTo("registration-username-check");
    assertThat(classifier.lastRequest.source()).isEqualTo("APIM_TAS");
    assertThat(classifier.lastRequest.endpoint()).isEqualTo("TRPChkCustId");
    assertThat(classifier.lastRequest.downstreamErrorCode()).isEqualTo("err.tpa.username.taken");
    assertThat(sessions.updated).isNull();
  }

  @Test
  void downstreamFailedBypassesClassifierAndDoesNotStoreUsernameCheck() {
    var sessions = FakeSessions.with(session(AuthFlowStatus.OTP_VERIFIED, "csrf", null));
    var availability =
        new FakeAvailability(RegistrationUsernameAvailabilityResult.downstreamFailed());
    var classifier = new FakeClassifier("downstream.generic.businessRejected");
    var service = service(sessions, availability, classifier);

    StepVerifier.create(service.check(command("csrf", selectedTrust("TL", "ENV1"), "abc1@")))
        .assertNext(
            result ->
                assertThat(result.status())
                    .isEqualTo(CheckRegistrationUsernameResultStatus.DOWNSTREAM_FAILED))
        .verifyComplete();

    assertThat(classifier.calls).isZero();
    assertThat(sessions.updated).isNull();
  }

  @Test
  void sameContextRepeatWhileUsernameVerifiedDoesNotCallAvailabilityOrMutateSession() {
    var existing =
        new UsernameCheckState(
            new SelectedTrust("TL", "ENV1"), "abc1@", NOW.minusSeconds(60), null);
    var sessions = FakeSessions.with(session(AuthFlowStatus.USERNAME_VERIFIED, "csrf", existing));
    var availability = new FakeAvailability(RegistrationUsernameAvailabilityResult.available());
    var service =
        service(
            sessions, availability, new FakeClassifier("err.registration.username.isRegistered"));

    StepVerifier.create(service.check(command("csrf", selectedTrust("TL", "ENV1"), "abc1@")))
        .assertNext(
            result -> {
              assertThat(result.status())
                  .isEqualTo(CheckRegistrationUsernameResultStatus.USERNAME_AVAILABLE);
              assertThat(result.nextStep()).isEqualTo(WorkflowNextStep.PASSWORD_SETUP);
            })
        .verifyComplete();

    assertThat(availability.calls).isZero();
    assertThat(sessions.updated).isNull();
  }

  @Test
  void failedReplacementWhileUsernameVerifiedKeepsPreviousUsernameCheck() {
    var existing =
        new UsernameCheckState(
            new SelectedTrust("TL", "ENV1"), "old1@", NOW.minusSeconds(60), null);
    var sessions = FakeSessions.with(session(AuthFlowStatus.USERNAME_VERIFIED, "csrf", existing));
    var availability =
        new FakeAvailability(
            RegistrationUsernameAvailabilityResult.rejected("err.tpa.username.taken"));
    var service =
        service(
            sessions, availability, new FakeClassifier("err.registration.username.isRegistered"));

    StepVerifier.create(service.check(command("csrf", selectedTrust("TL", "ENV1"), "new1@")))
        .assertNext(
            result ->
                assertThat(result.status())
                    .isEqualTo(CheckRegistrationUsernameResultStatus.DOWNSTREAM_REJECTED))
        .verifyComplete();

    assertThat(sessions.updated).isNull();
  }

  @Test
  void rejectsMutationFromCompleting() {
    var existing =
        new UsernameCheckState(
            new SelectedTrust("TL", "ENV1"), "old1@", NOW.minusSeconds(60), null);
    var sessions = FakeSessions.with(session(AuthFlowStatus.COMPLETING, "csrf", existing));
    var availability = new FakeAvailability(RegistrationUsernameAvailabilityResult.available());
    var service =
        service(
            sessions, availability, new FakeClassifier("err.registration.username.isRegistered"));

    StepVerifier.create(service.check(command("csrf", selectedTrust("TL", "ENV1"), "new1@")))
        .assertNext(
            result ->
                assertThat(result.status())
                    .isEqualTo(CheckRegistrationUsernameResultStatus.INVALID_STATE))
        .verifyComplete();

    assertThat(availability.calls).isZero();
    assertThat(sessions.updated).isNull();
  }

  private CheckRegistrationUsernameService service(
      FakeSessions sessions, FakeAvailability availability, FakeClassifier classifier) {
    return new CheckRegistrationUsernameService(
        sessions,
        expiryRefresh(sessions),
        availability,
        new FakePolicy(),
        classifier,
        new FakeUsernameCheckProtector(),
        CLOCK);
  }

  private AuthFlowSessionExpiryRefreshPort expiryRefresh(FakeSessions sessions) {
    return (authFlowSessionId, interactionAt) -> {
      if (sessions.session == null) {
        return Mono.error(new IllegalStateException("Auth flow session missing"));
      }
      AuthFlowSession current = sessions.session;
      AuthFlowSession refreshed =
          new AuthFlowSession(
              current.authFlowSessionId(),
              current.scenario(),
              current.status(),
              current.csrfTokenHash(),
              current.activeOtpChallengeId(),
              current.data(),
              current.createdAt(),
              interactionAt,
              interactionAt.plusSeconds(1800));
      sessions.session = refreshed;
      return Mono.just(refreshed);
    };
  }

  private CheckRegistrationUsernameCommand command(
      String csrf, RegistrationSelectedTrust selectedTrust, String username) {
    return new CheckRegistrationUsernameCommand(
        new AuthFlowSessionId("afs_secret"),
        csrf,
        selectedTrust,
        username,
        "https://portal.test",
        "https://portal.test/register",
        "en",
        "req-1");
  }

  private RegistrationSelectedTrust selectedTrust(String trustCode, String accountEnv) {
    return new RegistrationSelectedTrust(trustCode, accountEnv);
  }

  private AuthFlowSession session(
      AuthFlowStatus status, String rawCsrfToken, UsernameCheckState usernameCheck) {
    return new AuthFlowSession(
        new AuthFlowSessionId("afs_secret"),
        AuthFlowScenario.REGISTRATION,
        status,
        sha256(rawCsrfToken),
        "otp_secret",
        new AuthFlowSessionData(
            "EMAIL", "m****r@example.com", usernameCheckAttributes(usernameCheck)),
        NOW.minusSeconds(300),
        NOW.minusSeconds(60),
        NOW.plusSeconds(1800));
  }

  private java.util.Map<String, String> usernameCheckAttributes(UsernameCheckState usernameCheck) {
    var attributes = new java.util.LinkedHashMap<String, String>();
    attributes.put(
        RegistrationEligibleTrustSet.ATTRIBUTE_NAME,
        RegistrationEligibleTrustSet.encode(
            java.util.List.of(
                new RegistrationLookupAccount("ENV1", "TL", null, null, null, null, null, Boolean.FALSE, null))));
    if (usernameCheck != null) {
      RegistrationUsernameCheckEncryptedState encrypted =
          new FakeUsernameCheckProtector().protect(usernameCheck);
      attributes.put(
          RegistrationUsernameCheckAttributeNames.USERNAME_CHECK_CIPHERTEXT,
          encrypted.ciphertext());
      attributes.put(
          RegistrationUsernameCheckAttributeNames.USERNAME_CHECK_KEY_VERSION,
          encrypted.keyVersion());
      attributes.put(
          RegistrationUsernameCheckAttributeNames.USERNAME_CHECK_ALGORITHM, encrypted.algorithm());
    }
    return java.util.Map.copyOf(attributes);
  }

  private static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static final class FakeSessions implements AuthFlowSessionPort {
    AuthFlowSession session;
    AuthFlowSession updated;

    static FakeSessions empty() {
      return new FakeSessions(null);
    }

    static FakeSessions with(AuthFlowSession session) {
      return new FakeSessions(session);
    }

    FakeSessions(AuthFlowSession session) {
      this.session = session;
    }

    @Override
    public Mono<AuthFlowSession> create(AuthFlowSessionCreateCommand command) {
      return Mono.error(new UnsupportedOperationException());
    }

    @Override
    public Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId authFlowSessionId) {
      return Mono.just(Optional.ofNullable(session));
    }

    @Override
    public Mono<AuthFlowSession> update(AuthFlowSession session) {
      this.updated = session;
      this.session = session;
      return Mono.just(session);
    }

    @Override
    public Mono<Optional<AuthFlowSessionSnapshot>> loadSnapshot(AuthFlowSessionId id) {
      return Mono.just(
          Optional.ofNullable(session)
              .map(
                  value ->
                      new AuthFlowSessionSnapshot(
                          value, new SessionRevision("00000000000000000000000000000000"))));
    }

    @Override
    public Mono<AuthFlowSessionMutationResult> updateRevisioned(
        RevisionedAuthFlowSessionMutationCommand command) {
      this.updated = command.updated();
      this.session = command.updated();
      return Mono.just(
          new AuthFlowSessionMutationResult(
              AuthFlowSessionMutationOutcome.APPLIED,
              new AuthFlowSessionSnapshot(
                  command.updated(), new SessionRevision("11111111111111111111111111111111"))));
    }

    @Override
    public Mono<AuthFlowSession> touch(AuthFlowSessionId authFlowSessionId, Instant now) {
      return Mono.error(new UnsupportedOperationException());
    }

    @Override
    public Mono<AuthFlowSession> complete(AuthFlowSessionId authFlowSessionId, Instant now) {
      return Mono.error(new UnsupportedOperationException());
    }

    @Override
    public Mono<AuthFlowSession> cancel(AuthFlowSessionId authFlowSessionId, Instant now) {
      return Mono.error(new UnsupportedOperationException());
    }

    @Override
    public Mono<Void> delete(AuthFlowSessionId authFlowSessionId) {
      return Mono.empty();
    }
  }

  private static final class FakeAvailability implements RegistrationUsernameAvailabilityPort {
    final RegistrationUsernameAvailabilityResult result;
    int calls;
    RegistrationUsernameAvailabilityCommand lastCommand;

    FakeAvailability(RegistrationUsernameAvailabilityResult result) {
      this.result = result;
    }

    @Override
    public Mono<RegistrationUsernameAvailabilityResult> checkAvailability(
        RegistrationUsernameAvailabilityCommand command) {
      calls++;
      lastCommand = command;
      return Mono.just(result);
    }
  }

  private static final class FakePolicy implements AuthWorkflowPolicyPort {
    @Override
    public RegistrationAuthWorkflowPolicy registrationPolicy() {
      return new RegistrationAuthWorkflowPolicy(
          null,
          null,
          null,
          new UsernamePolicy(4, 128, true, true, true, true, "!@#$%^&*()_-"),
          null);
    }

    @Override
    public java.util.Optional<String> resolveIdentityTypeSearchScope(String identityType) {
      return java.util.Optional.empty();
    }
  }

  private static final class FakeUsernameCheckProtector
      implements RegistrationUsernameCheckProtectorPort {
    @Override
    public RegistrationUsernameCheckEncryptedState protect(UsernameCheckState usernameCheckState) {
      return new RegistrationUsernameCheckEncryptedState(
          "encrypted-username-check", "test-v1", "AES_GCM_V1");
    }

    @Override
    public UsernameCheckState unprotect(RegistrationUsernameCheckEncryptedState encryptedState) {
      return new UsernameCheckState(
          new SelectedTrust("TL", "ENV1"), "abc1@", NOW.minusSeconds(60), null);
    }
  }

  private static final class FakeClassifier implements DownstreamErrorClassificationPort {
    final String responseCode;
    int calls;
    DownstreamErrorClassificationRequest lastRequest;

    FakeClassifier(String responseCode) {
      this.responseCode = responseCode;
    }

    @Override
    public DownstreamErrorClassification classify(DownstreamErrorClassificationRequest request) {
      calls++;
      lastRequest = request;
      return new DownstreamErrorClassification(
          "DOWNSTREAM_REJECTION", "ERROR", responseCode, 422, "PAGE", false, null, true);
    }
  }

  // ----- session.isExpired() / wrong scenario (added round 203) -----------------

  @Test
  void rejectsExpiredSessionAndDoesNotCallAvailability() {
    var expired =
        new AuthFlowSession(
            new AuthFlowSessionId("afs_secret"),
            AuthFlowScenario.REGISTRATION,
            AuthFlowStatus.OTP_VERIFIED,
            sha256("csrf"),
            "otp_secret",
            new AuthFlowSessionData("EMAIL", "m****r@example.com", java.util.Map.of()),
            NOW.minusSeconds(3600),
            NOW.minusSeconds(3600),
            NOW.minusSeconds(1));
    var sessions = FakeSessions.with(expired);
    var availability = new FakeAvailability(RegistrationUsernameAvailabilityResult.available());
    var service =
        service(
            sessions, availability, new FakeClassifier("err.registration.username.isRegistered"));
    StepVerifier.create(service.check(command("csrf", selectedTrust("TL", "ENV1"), "abc1@")))
        .assertNext(
            result ->
                assertThat(result.status())
                    .isEqualTo(CheckRegistrationUsernameResultStatus.INVALID_AUTH_FLOW))
        .verifyComplete();
    assertThat(availability.calls).isZero();
  }

  @Test
  void rejectsWrongScenarioAndDoesNotCallAvailability() {
    var wrongScenario =
        new AuthFlowSession(
            new AuthFlowSessionId("afs_secret"),
            AuthFlowScenario.LOGIN,
            AuthFlowStatus.OTP_VERIFIED,
            sha256("csrf"),
            "otp_secret",
            new AuthFlowSessionData("EMAIL", "m****r@example.com", java.util.Map.of()),
            NOW.minusSeconds(300),
            NOW.minusSeconds(60),
            NOW.plusSeconds(1800));
    var sessions = FakeSessions.with(wrongScenario);
    var availability = new FakeAvailability(RegistrationUsernameAvailabilityResult.available());
    var service =
        service(
            sessions, availability, new FakeClassifier("err.registration.username.isRegistered"));
    StepVerifier.create(service.check(command("csrf", selectedTrust("TL", "ENV1"), "abc1@")))
        .assertNext(
            result ->
                assertThat(result.status())
                    .isEqualTo(CheckRegistrationUsernameResultStatus.INVALID_AUTH_FLOW))
        .verifyComplete();
    assertThat(availability.calls).isZero();
  }

  // ----- validUsername() sub-branches (added round 203) --------------------------

  @Test
  void rejectsUsernameShorterThanMinLength() {
    var sessions = FakeSessions.with(session(AuthFlowStatus.OTP_VERIFIED, "csrf", null));
    var availability = new FakeAvailability(RegistrationUsernameAvailabilityResult.available());
    var service =
        service(
            sessions, availability, new FakeClassifier("err.registration.username.isRegistered"));
    StepVerifier.create(service.check(command("csrf", selectedTrust("TL", "ENV1"), "a1")))
        .assertNext(
            result ->
                assertThat(result.status())
                    .isEqualTo(CheckRegistrationUsernameResultStatus.VALIDATION_FAILED))
        .verifyComplete();
  }

  @Test
  void rejectsUsernameLongerThanMaxLength() {
    var sessions = FakeSessions.with(session(AuthFlowStatus.OTP_VERIFIED, "csrf", null));
    var availability = new FakeAvailability(RegistrationUsernameAvailabilityResult.available());
    var service =
        service(
            sessions, availability, new FakeClassifier("err.registration.username.isRegistered"));
    String tooLong = "a1".repeat(100);
    StepVerifier.create(service.check(command("csrf", selectedTrust("TL", "ENV1"), tooLong)))
        .assertNext(
            result ->
                assertThat(result.status())
                    .isEqualTo(CheckRegistrationUsernameResultStatus.VALIDATION_FAILED))
        .verifyComplete();
  }

  @Test
  void rejectsUsernameContainingWhitespace() {
    var sessions = FakeSessions.with(session(AuthFlowStatus.OTP_VERIFIED, "csrf", null));
    var availability = new FakeAvailability(RegistrationUsernameAvailabilityResult.available());
    var service =
        service(
            sessions, availability, new FakeClassifier("err.registration.username.isRegistered"));
    StepVerifier.create(service.check(command("csrf", selectedTrust("TL", "ENV1"), "abc 1@")))
        .assertNext(
            result ->
                assertThat(result.status())
                    .isEqualTo(CheckRegistrationUsernameResultStatus.VALIDATION_FAILED))
        .verifyComplete();
  }

  @Test
  void rejectsUsernameWithDisallowedCharacters() {
    var sessions = FakeSessions.with(session(AuthFlowStatus.OTP_VERIFIED, "csrf", null));
    var availability = new FakeAvailability(RegistrationUsernameAvailabilityResult.available());
    var service =
        service(
            sessions, availability, new FakeClassifier("err.registration.username.isRegistered"));
    StepVerifier.create(service.check(command("csrf", selectedTrust("TL", "ENV1"), "abc1<>")))
        .assertNext(
            result ->
                assertThat(result.status())
                    .isEqualTo(CheckRegistrationUsernameResultStatus.VALIDATION_FAILED))
        .verifyComplete();
  }

  // ----- publicUsernameRejectionCode() 3-way branch (added round 203) ------------------

  @Test
  void mapsCustIdDuplicatedToIsRegisteredCode() {
    var sessions = FakeSessions.with(session(AuthFlowStatus.OTP_VERIFIED, "csrf", null));
    var availability =
        new FakeAvailability(
            RegistrationUsernameAvailabilityResult.rejected("err.tpa.cust.id.duplicated"));
    var service =
        service(
            sessions, availability, new FakeClassifier("err.registration.username.isRegistered"));
    StepVerifier.create(service.check(command("csrf", selectedTrust("TL", "ENV1"), "abc1@")))
        .assertNext(
            result ->
                assertThat(result.errors().getFirst().code())
                    .isEqualTo("err.registration.username.isRegistered"))
        .verifyComplete();
  }

  @Test
  void mapsMemberRegisteredToIsRegisteredCode() {
    var sessions = FakeSessions.with(session(AuthFlowStatus.OTP_VERIFIED, "csrf", null));
    var availability =
        new FakeAvailability(
            RegistrationUsernameAvailabilityResult.rejected("err.tpa.member.registered"));
    var service =
        service(
            sessions, availability, new FakeClassifier("err.registration.username.isRegistered"));
    StepVerifier.create(service.check(command("csrf", selectedTrust("TL", "ENV1"), "abc1@")))
        .assertNext(
            result ->
                assertThat(result.errors().getFirst().code())
                    .isEqualTo("err.registration.username.isRegistered"))
        .verifyComplete();
  }

  @Test
  void passesThroughClassifiedRegistrationUsernameCode() {
    var sessions = FakeSessions.with(session(AuthFlowStatus.OTP_VERIFIED, "csrf", null));
    var availability =
        new FakeAvailability(
            RegistrationUsernameAvailabilityResult.rejected("err.tpa.some.other.rejection"));
    var service =
        service(
            sessions,
            availability,
            new FakeClassifier("err.registration.username.someOtherClassifiedCode"));
    StepVerifier.create(service.check(command("csrf", selectedTrust("TL", "ENV1"), "abc1@")))
        .assertNext(
            result ->
                assertThat(result.errors().getFirst().code())
                    .isEqualTo("err.registration.username.someOtherClassifiedCode"))
        .verifyComplete();
  }

  @Test
  void fallsBackToCheckFailedWhenClassifierCodeIsUnrelated() {
    var sessions = FakeSessions.with(session(AuthFlowStatus.OTP_VERIFIED, "csrf", null));
    var availability =
        new FakeAvailability(
            RegistrationUsernameAvailabilityResult.rejected("err.tpa.some.other.rejection"));
    var service = service(sessions, availability, new FakeClassifier("downstream.generic.other"));
    StepVerifier.create(service.check(command("csrf", selectedTrust("TL", "ENV1"), "abc1@")))
        .assertNext(
            result ->
                assertThat(result.errors().getFirst().code())
                    .isEqualTo("err.registration.username.checkFailed"))
        .verifyComplete();
  }

  @Test
  void blankDownstreamErrorCodeClassifiesAsUnknown() {
    var sessions = FakeSessions.with(session(AuthFlowStatus.OTP_VERIFIED, "csrf", null));
    var availability = new FakeAvailability(RegistrationUsernameAvailabilityResult.rejected("  "));
    var classifier = new FakeClassifier("err.registration.username.isRegistered");
    var service = service(sessions, availability, classifier);
    StepVerifier.create(service.check(command("csrf", selectedTrust("TL", "ENV1"), "abc1@")))
        .assertNext(result -> assertThat(result).isNotNull())
        .verifyComplete();
    assertThat(classifier.lastRequest.downstreamErrorCode()).isEqualTo("unknown");
  }

  // ----- existingUsernameCheck() RuntimeException catch (added round 203) ------------

  @Test
  void corruptedUsernameCheckStateIsIgnoredAndAvailabilityIsCalledAgain() {
    var existing =
        new UsernameCheckState(
            new SelectedTrust("TL", "ENV1"), "abc1@", NOW.minusSeconds(60), null);
    var sessions = FakeSessions.with(session(AuthFlowStatus.USERNAME_VERIFIED, "csrf", existing));
    var availability = new FakeAvailability(RegistrationUsernameAvailabilityResult.available());
    var throwingProtector = new ThrowingUnprotectUsernameCheckProtector();
    var service =
        new CheckRegistrationUsernameService(
            sessions,
            expiryRefresh(sessions),
            availability,
            new FakePolicy(),
            new FakeClassifier("err.registration.username.isRegistered"),
            throwingProtector,
            CLOCK);
    StepVerifier.create(service.check(command("csrf", selectedTrust("TL", "ENV1"), "abc1@")))
        .assertNext(
            result ->
                assertThat(result.status())
                    .isEqualTo(CheckRegistrationUsernameResultStatus.USERNAME_AVAILABLE))
        .verifyComplete();
    assertThat(availability.calls).isOne();
  }

  private static final class ThrowingUnprotectUsernameCheckProtector
      implements RegistrationUsernameCheckProtectorPort {
    @Override
    public RegistrationUsernameCheckEncryptedState protect(UsernameCheckState usernameCheckState) {
      return new RegistrationUsernameCheckEncryptedState(
          "encrypted-username-check", "test-v1", "AES_GCM_V1");
    }

    @Override
    public UsernameCheckState unprotect(RegistrationUsernameCheckEncryptedState encryptedState) {
      throw new IllegalStateException("corrupted state");
    }
  }
}
