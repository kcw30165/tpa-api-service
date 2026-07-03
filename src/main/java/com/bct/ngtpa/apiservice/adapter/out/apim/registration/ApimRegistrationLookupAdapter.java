package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpSearchUnregisteredMemberApimMapper;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpSearchUnregisteredMemberApimResultMapper;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupResult;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationLookupPort;
import java.util.Objects;
import reactor.core.publisher.Mono;

public class ApimRegistrationLookupAdapter implements RegistrationLookupPort {
    static final String TRP_SEARCH_UNREGISTERED_MEMBER_PATH = "/ws/NGTPA/v1/TRPSearchUnregisteredMember";

    private final ApimRegistrationLookupTransport transport;
    private final TrpSearchUnregisteredMemberApimMapper requestMapper;
    private final TrpSearchUnregisteredMemberApimResultMapper resultMapper;

    public ApimRegistrationLookupAdapter(
            ApimRegistrationLookupTransport transport,
            TrpSearchUnregisteredMemberApimMapper requestMapper,
            TrpSearchUnregisteredMemberApimResultMapper resultMapper) {
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
        this.requestMapper = Objects.requireNonNull(requestMapper, "requestMapper must not be null");
        this.resultMapper = Objects.requireNonNull(resultMapper, "resultMapper must not be null");
    }

    @Override
    public Mono<RegistrationLookupResult> searchUnregisteredMember(RegistrationLookupCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        var request = requestMapper.toApimRequest(command);
        return transport.postSearchUnregisteredMember(TRP_SEARCH_UNREGISTERED_MEMBER_PATH, request)
                .map(envelope -> resultMapper.toResult(envelope.errMessage(), envelope.data()));
    }

    @Override
    public String toString() {
        return "ApimRegistrationLookupAdapter[path=" + TRP_SEARCH_UNREGISTERED_MEMBER_PATH + "]";
    }
}
