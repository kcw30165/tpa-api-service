package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword;

import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.mapper.TrpChkCustIdForgotPasswordResultMapper;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.SharedApimTrpChkCustIdTransport;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.TrpChkCustIdApimEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpChkCustIdApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import org.springframework.http.HttpStatus;
import java.util.List;
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
    private final ApimExchangeExecutor executor;
    private final TrpChkCustIdForgotPasswordResultMapper resultMapper;

    public ApimForgotPasswordUsernameLookupAdapter(
            ApimExchangeExecutor executor,
            Map<String, MemberIdentityType> tasToPublicIdentityTypeMapping) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.resultMapper = new TrpChkCustIdForgotPasswordResultMapper(tasToPublicIdentityTypeMapping);
    }

    @Override
    public Mono<ForgotPasswordUsernameLookupResult> lookup(StartForgotPasswordUsernameLookupCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        var request = new TrpChkCustIdApimRequest(command.normalizedUsername());
        var context = new ApimCredentialResolutionContext(command.actorUserRole(), null);
        var exchange = new ApimExchangeRequest<>(
                SharedApimTrpChkCustIdTransport.API_NAME,
                request,
                TrpChkCustIdApimRequest.class,
                TrpChkCustIdApimDataItem.class,
                ApimDatabaseScope.NON_ACCOUNT,
                context,
                ApimRetryEligibility.SAFE_BUSINESS_CALL);
        return executor.postEnvelope(exchange)
                .map(this::toTransportEnvelope)
                .map(resultMapper::toResult)
                .doOnNext(result -> LOGGER.info(
                        "event=forgot-password.username-lookup.completed requestId={} status={}",
                        command.correlationId(), result.status()))
                .doOnError(error -> LOGGER.error(
                        "event=forgot-password.username-lookup.failed requestId={} exceptionType={}",
                        command.correlationId(), error.getClass().getName(), error));
    }

    private TrpChkCustIdApimEnvelope toTransportEnvelope(
            ApimResponseEnvelope<TrpChkCustIdApimDataItem> envelope) {
        if (envelope == null || envelope.getResponse() == null) {
            throw new IllegalStateException("TRPChkCustId response envelope is missing response");
        }
        String errMessage = envelope.getResponse().getErrMessage();
        List<TrpChkCustIdApimDataItem> data = envelope.getResponse().getData();
        boolean err = errMessage != null && !errMessage.isBlank();
        boolean hasData = data != null && !data.isEmpty();
        if (!err && !hasData) {
            throw new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID,
                    "TRPChkCustId returned an invalid/malformed response");
        }
        if (hasData && data.stream().noneMatch(item -> item != null
                && (item.customerId() != null || item.isExist() != null))) {
            throw new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID,
                    "TRPChkCustId returned data items but they are malformed");
        }
        return new TrpChkCustIdApimEnvelope(errMessage, data);
    }
}
