package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimCertificateService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimPayloadCryptoService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimWebClientFacade;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the shared TRPChkCustId transport once for all authentication workflows. */
@Configuration
public class SharedApimTrpChkCustIdTransportConfig {
    @Bean
    @ConditionalOnMissingBean(SharedApimTrpChkCustIdTransport.class)
    SharedApimTrpChkCustIdTransport sharedApimTrpChkCustIdTransport(
            ApimWebClientFacade facade,
            ApimCertificateService certificates,
            ApimPayloadCryptoService crypto,
            ApimProperties properties) {
        return new SharedApimTrpChkCustIdTransport(
                facade,
                certificates,
                crypto,
                properties);
    }
}
