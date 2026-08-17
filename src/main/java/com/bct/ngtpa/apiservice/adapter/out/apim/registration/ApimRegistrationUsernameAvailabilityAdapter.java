package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpChkCustIdApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpChkCustIdApimMapper;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpChkCustIdApimResultMapper;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationUsernameAvailabilityPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.RegistrationUsernameAvailabilityCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.RegistrationUsernameAvailabilityResult;
import com.bct.ngtpa.apiservice.shared.logging.LogExecution;
import java.util.Objects;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ApimRegistrationUsernameAvailabilityAdapter implements RegistrationUsernameAvailabilityPort {
    private final TrpChkCustIdTransport transport;
    private final TrpChkCustIdApimMapper requestMapper = new TrpChkCustIdApimMapper();
    private final TrpChkCustIdApimResultMapper resultMapper = new TrpChkCustIdApimResultMapper();

    public ApimRegistrationUsernameAvailabilityAdapter(TrpChkCustIdTransport transport) {
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
    }

    @Override
    @LogExecution(value = "apim.trpChkCustId", logArgs = false)
    public Mono<RegistrationUsernameAvailabilityResult> checkAvailability(
            RegistrationUsernameAvailabilityCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        TrpChkCustIdApimRequest request = requestMapper.toApimRequest(command);
        return transport.post(new TrpChkCustIdTransportCommand(
                        request,
                        command.actorUserRole(),
                        command.correlationId()))
                .map(resultMapper::toResult);
    }
}
