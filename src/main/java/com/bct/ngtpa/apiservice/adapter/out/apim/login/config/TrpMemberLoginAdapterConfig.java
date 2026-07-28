package com.bct.ngtpa.apiservice.adapter.out.apim.login.config;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimCertificateService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimPayloadCryptoService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimWebClientFacade;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.login.SharedApimTrpMemberLoginTransport;
import com.bct.ngtpa.apiservice.adapter.out.apim.login.TrpMemberLoginAdapter;
import com.bct.ngtpa.apiservice.adapter.out.apim.login.TrpMemberLoginContractMapper;
import com.bct.ngtpa.apiservice.adapter.out.apim.login.TrpMemberLoginTransport;
import com.bct.ngtpa.apiservice.application.port.out.CredentialAuthenticationPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TrpMemberLoginProperties.class)
public class TrpMemberLoginAdapterConfig {

    @Bean
    TrpMemberLoginContractMapper trpMemberLoginContractMapper() {
        return new TrpMemberLoginContractMapper();
    }

    @Bean
    TrpMemberLoginTransport trpMemberLoginTransport(
            ApimWebClientFacade facade,
            ApimCertificateService certificates,
            ApimPayloadCryptoService crypto,
            ApimProperties apimProperties,
            TrpMemberLoginProperties loginProperties) {
        return new SharedApimTrpMemberLoginTransport(
                facade,
                certificates,
                crypto,
                apimProperties,
                loginProperties);
    }

    @Bean
    CredentialAuthenticationPort credentialAuthenticationPort(
            TrpMemberLoginTransport transport,
            TrpMemberLoginContractMapper mapper) {
        return new TrpMemberLoginAdapter(transport, mapper);
    }
}
