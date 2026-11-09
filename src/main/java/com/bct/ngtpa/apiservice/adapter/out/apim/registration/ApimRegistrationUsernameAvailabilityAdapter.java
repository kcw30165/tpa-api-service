package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimFailureCategory;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimFailureClassifier;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimIncidentOwnership;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpChkCustIdApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpChkCustIdApimMapper;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpChkCustIdApimResultMapper;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationUsernameAvailabilityPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.RegistrationUsernameAvailabilityCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.RegistrationUsernameAvailabilityFailureOrigin;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.RegistrationUsernameAvailabilityResult;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class ApimRegistrationUsernameAvailabilityAdapter
    implements RegistrationUsernameAvailabilityPort {
  private final TrpChkCustIdTransport transport;
  private final TrpChkCustIdApimMapper requestMapper = new TrpChkCustIdApimMapper();
  private final TrpChkCustIdApimResultMapper resultMapper = new TrpChkCustIdApimResultMapper();

  public ApimRegistrationUsernameAvailabilityAdapter(TrpChkCustIdTransport transport) {
    this.transport = Objects.requireNonNull(transport, "transport must not be null");
  }

  @Override
  public Mono<RegistrationUsernameAvailabilityResult> checkAvailability(
      RegistrationUsernameAvailabilityCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    TrpChkCustIdApimRequest request = requestMapper.toApimRequest(command);
    return transport
        .post(
            new TrpChkCustIdTransportCommand(
                request,
                command.actorUserRole(),
                command.correlationId(),
                ApimIncidentOwnership.TERMINAL_CALLER))
        .map(resultMapper::toResult)
        .onErrorResume(
            failure -> {
              ApimFailureCategory category = ApimFailureClassifier.classify(failure);
              log.warn(
                  "event=registration.usernameCheck.apimFailureDiagnostic category={} correlationId={}",
                  category,
                  command.correlationId());
              RegistrationUsernameAvailabilityFailureOrigin origin =
                  switch (category) {
                    case LOCAL_SYSTEM -> RegistrationUsernameAvailabilityFailureOrigin.LOCAL_SYSTEM;
                    case DOWNSTREAM_CONTRACT, DOWNSTREAM_INVALID_RESPONSE, DOWNSTREAM_TRANSPORT ->
                        RegistrationUsernameAvailabilityFailureOrigin.APIM_TECHNICAL;
                  };
              return Mono.just(RegistrationUsernameAvailabilityResult.downstreamFailed(origin));
            });
  }
}
