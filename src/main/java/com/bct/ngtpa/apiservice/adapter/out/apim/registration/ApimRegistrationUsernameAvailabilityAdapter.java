package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpChkCustIdApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpChkCustIdApimMapper;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpChkCustIdApimResultMapper;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationUsernameAvailabilityPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.RegistrationUsernameAvailabilityCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.RegistrationUsernameAvailabilityResult;
import com.bct.ngtpa.apiservice.shared.logging.LogExecution;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ApimRegistrationUsernameAvailabilityAdapter implements RegistrationUsernameAvailabilityPort {
    private final TrpChkCustIdTransport transport;
    private final TrpChkCustIdApimMapper requestMapper;
    private final TrpChkCustIdApimResultMapper resultMapper;

    @Autowired
    public ApimRegistrationUsernameAvailabilityAdapter(
            SharedApimTrpChkCustIdTransport transport) {
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
        this.requestMapper = new TrpChkCustIdApimMapper();
        this.resultMapper = new TrpChkCustIdApimResultMapper();
    }

    public ApimRegistrationUsernameAvailabilityAdapter(
            ApimRegistrationUsernameAvailabilityTransport testTransport) {
        this.transport = (request, correlationId) -> Objects
                .requireNonNull(testTransport, "testTransport must not be null")
                .postChkCustId(SharedApimTrpChkCustIdTransport.API_NAME, request);
        this.requestMapper = new TrpChkCustIdApimMapper();
        this.resultMapper = new TrpChkCustIdApimResultMapper();
    }

    @Override
    @LogExecution(value = "apim.trpChkCustId", logArgs = false)
    public Mono<RegistrationUsernameAvailabilityResult> checkAvailability(
            RegistrationUsernameAvailabilityCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        TrpChkCustIdApimRequest request = requestMapper.toApimRequest(command);
        // Prefer caller-supplied authoritative accountEnv routing when present. The adapter is
        // constructed with the shared transport implementation so a safe concrete call is used.
        if (command != null && command.selectedTrust() != null && command.selectedTrust().accountEnv() != null) {
            String accountEnv = command.selectedTrust().accountEnv();
            if (transport instanceof SharedApimTrpChkCustIdTransport shared) {
                return shared.postWithAccountEnv(request, command.correlationId(), accountEnv)
                        .map(resultMapper::toResult);
            }
            // Test harnesses and alternate transports may not support account-env helper; fall back
            // to the generic transport.post which the test constructor wires to the test transport.
        }
        return transport.post(request, command.correlationId())
                .map(resultMapper::toResult);
    }
}
