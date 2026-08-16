package com.bct.ngtpa.apiservice.adapter.out.apim.login.account;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.login.account.dto.TrpGetAccountListApimDataItem;
import com.bct.ngtpa.apiservice.application.dto.auth.login.account.LoginAccountAuthorizationCommand;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface TrpGetAccountListTransport {
    Mono<ApimResponseEnvelope<TrpGetAccountListApimDataItem>> post(LoginAccountAuthorizationCommand command);
}
