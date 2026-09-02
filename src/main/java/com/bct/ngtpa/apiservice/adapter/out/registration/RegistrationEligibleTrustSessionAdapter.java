package com.bct.ngtpa.apiservice.adapter.out.registration;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationEligibleTrustSet;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupAccount;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationEligibleTrustPersistencePort;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.RevisionAwareAuthFlowSessionWriter;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

public final class RegistrationEligibleTrustSessionAdapter
    implements RegistrationEligibleTrustPersistencePort {
  private final AuthFlowSessionPort sessions;

  public RegistrationEligibleTrustSessionAdapter(AuthFlowSessionPort sessions) {
    this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
  }

  @Override
  public Mono<Void> store(
      AuthFlowSessionId sessionId, List<RegistrationLookupAccount> accounts, Instant now) {
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    Objects.requireNonNull(now, "now must not be null");
    String encoded = RegistrationEligibleTrustSet.encode(accounts);
    return sessions
        .load(sessionId)
        .flatMap(
            optional -> {
              AuthFlowSession current =
                  optional.orElseThrow(
                      () -> new IllegalStateException("Registration auth-flow session not found"));
              AuthFlowSessionData data =
                  current.data() == null ? AuthFlowSessionData.empty() : current.data();
              var attributes = new LinkedHashMap<>(data.attributes());
              attributes.put(RegistrationEligibleTrustSet.ATTRIBUTE_NAME, encoded);
              var updatedData =
                  new AuthFlowSessionData(data.contactType(), data.maskedDestination(), attributes);
              var updated =
                  new AuthFlowSession(
                      current.authFlowSessionId(),
                      current.scenario(),
                      current.status(),
                      current.csrfTokenHash(),
                      current.activeOtpChallengeId(),
                      updatedData,
                      current.createdAt(),
                      now,
                      current.expiresAt());
              return new RevisionAwareAuthFlowSessionWriter(sessions)
                  .updateExpected(current, updated)
                  .then();
            });
  }
}
