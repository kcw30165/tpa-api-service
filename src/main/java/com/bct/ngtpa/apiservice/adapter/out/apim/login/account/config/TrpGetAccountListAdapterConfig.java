package com.bct.ngtpa.apiservice.adapter.out.apim.login.account.config;

import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.login.account.*;
import com.bct.ngtpa.apiservice.application.port.out.LoginAccountAuthorizationPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.account.GetLoginAccountsUseCase;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;

@Configuration
@EnableConfigurationProperties(TrpGetAccountListProperties.class)
public class TrpGetAccountListAdapterConfig {
    @Bean TrpGetAccountListContractMapper trpGetAccountListContractMapper() { return new TrpGetAccountListContractMapper(); }
    @Bean TrpGetAccountListTransport trpGetAccountListTransport(ApimExchangeExecutor executor,
            TrpGetAccountListProperties properties) { return new SharedApimTrpGetAccountListTransport(executor, properties); }
    @Bean LoginAccountAuthorizationPort loginAccountAuthorizationPort(TrpGetAccountListTransport transport,
            TrpGetAccountListContractMapper mapper, TrpGetAccountListProperties properties) {
        return new TrpGetAccountListAdapter(transport, mapper, properties);
    }
    @Bean GetLoginAccountsUseCase getLoginAccountsUseCase(LoginAccountAuthorizationPort authorizationPort) {
        return new GetLoginAccountsUseCase(authorizationPort);
    }
}
