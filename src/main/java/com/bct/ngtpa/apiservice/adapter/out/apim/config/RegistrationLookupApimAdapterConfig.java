package com.bct.ngtpa.apiservice.adapter.out.apim.config;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpSearchUnregisteredMemberApimMapper;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpSearchUnregisteredMemberApimResultMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RegistrationLookupApimAdapterConfig {

    @Bean
    public TrpSearchUnregisteredMemberApimMapper trpSearchUnregisteredMemberApimMapper() {
        return new TrpSearchUnregisteredMemberApimMapper();
    }

    @Bean
    public TrpSearchUnregisteredMemberApimResultMapper trpSearchUnregisteredMemberApimResultMapper() {
        return new TrpSearchUnregisteredMemberApimResultMapper();
    }
}