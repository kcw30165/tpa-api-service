package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword;

import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.mapper.TrpChkCustIdForgotPasswordResultMapper;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.SharedApimTrpChkCustIdTransport;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpChkCustIdApimRequest;
import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordUsernameLookupPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.ForgotPasswordUsernameLookupResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.StartForgotPasswordUsernameLookupCommand;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ApimForgotPasswordUsernameLookupAdapter implements ForgotPasswordUsernameLookupPort {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApimForgotPasswordUsernameLookupAdapter.class);
    private final SharedApimTrpChkCustIdTransport transport;
    private final TrpChkCustIdForgotPasswordResultMapper resultMapper;

    public ApimForgotPasswordUsernameLookupAdapter(
            SharedApimTrpChkCustIdTransport transport,
            Map<String, MemberIdentityType> tasToPublicIdentityTypeMapping) {
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
        this.resultMapper = new TrpChkCustIdForgotPasswordResultMapper(tasToPublicIdentityTypeMapping);
    }

    @Override
    public Mono<ForgotPasswordUsernameLookupResult> lookup(StartForgotPasswordUsernameLookupCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return transport.post(new TrpChkCustIdApimRequest(command.normalizedUsername()), command.correlationId())
                .map(resultMapper::toResult)
                .doOnNext(result -> LOGGER.info(
                        "event=forgot-password.username-lookup.completed requestId={} status={}",
                        command.correlationId(), result.status()))
                .doOnError(error -> LOGGER.error(
                        "event=forgot-password.username-lookup.failed requestId={} exceptionType={}",
                        command.correlationId(), error.getClass().getName(), error));
    }
}