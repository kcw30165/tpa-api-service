package com.bct.ngtpa.apiservice.adapter.out.apim.login;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.login.config.TrpMemberLoginProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.login.dto.TrpMemberLoginApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.login.dto.TrpMemberLoginApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationCommand;
import java.util.Objects;
import reactor.core.publisher.Mono;

public final class SharedApimTrpMemberLoginTransport implements TrpMemberLoginTransport {
    private final ApimExchangeExecutor executor;
    private final TrpMemberLoginProperties loginProperties;

    public SharedApimTrpMemberLoginTransport(ApimExchangeExecutor executor, TrpMemberLoginProperties loginProperties) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.loginProperties = Objects.requireNonNull(loginProperties, "loginProperties must not be null");
    }

    @Override
    public Mono<ApimResponseEnvelope<TrpMemberLoginApimDataItem>> post(CredentialAuthenticationCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        String path = requireText(loginProperties.getPath(), "apim.login.member-login.path");
        var request = new TrpMemberLoginApimRequest(command.username(), command.password());
        var exchange = new ApimExchangeRequest<>(path, request,
                TrpMemberLoginApimRequest.class, TrpMemberLoginApimDataItem.class,
                ApimDatabaseScope.NON_ACCOUNT,
                new ApimCredentialResolutionContext(command.actorUserRole(), null),
                ApimRetryEligibility.SAFE_BUSINESS_CALL);
        return executor.postEnvelope(exchange);
    }

    private String requireText(String value, String property) {
        if (value == null || value.isBlank()) throw new IllegalStateException(property + " must not be blank");
        return value.trim();
    }
}
