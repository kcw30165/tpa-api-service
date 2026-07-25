package com.bct.ngtpa.apiservice.adapter.out.apim.login.account.config;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.account.GetLoginAccountsUseCase;
import com.bct.ngtpa.apiservice.adapter.out.apim.*;import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;import com.bct.ngtpa.apiservice.adapter.out.apim.login.account.*;import com.bct.ngtpa.apiservice.application.port.out.LoginAccountAuthorizationPort;import org.springframework.boot.context.properties.EnableConfigurationProperties;import org.springframework.context.annotation.*;
@Configuration @EnableConfigurationProperties(TrpGetAccountListProperties.class)
public class TrpGetAccountListAdapterConfig {
 @Bean TrpGetAccountListContractMapper trpGetAccountListContractMapper(){return new TrpGetAccountListContractMapper();}
 @Bean TrpGetAccountListTransport trpGetAccountListTransport(ApimWebClientFacade facade,ApimCertificateService certificates,ApimPayloadCryptoService crypto,ApimProperties apim,TrpGetAccountListProperties properties){return new SharedApimTrpGetAccountListTransport(facade,certificates,crypto,apim,properties);}
 @Bean LoginAccountAuthorizationPort loginAccountAuthorizationPort(TrpGetAccountListTransport transport,TrpGetAccountListContractMapper mapper,TrpGetAccountListProperties properties){return new TrpGetAccountListAdapter(transport,mapper,properties);}


    @Bean
    GetLoginAccountsUseCase getLoginAccountsUseCase(
            LoginAccountAuthorizationPort authorizationPort) {
        return new GetLoginAccountsUseCase(authorizationPort);
    }
}
