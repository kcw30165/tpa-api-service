package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SharedApimTrpChkCustIdTransportConfig {
    @Bean
    @ConditionalOnMissingBean(SharedApimTrpChkCustIdTransport.class)
    SharedApimTrpChkCustIdTransport sharedApimTrpChkCustIdTransport(ApimExchangeExecutor executor) {
        return new SharedApimTrpChkCustIdTransport(executor);
    }
}
