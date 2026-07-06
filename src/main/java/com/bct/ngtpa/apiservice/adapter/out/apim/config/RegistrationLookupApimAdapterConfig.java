package com.bct.ngtpa.apiservice.adapter.out.apim.config;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimWebClientFacade;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.ApimRegistrationLookupAdapter;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.ApimRegistrationLookupTransport;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpSearchUnregisteredMemberApimMapper;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpSearchUnregisteredMemberApimResultMapper;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationLookupPort;
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

    // @Bean
    // public RegistrationLookupPort registrationLookupPort(
    //         ApimRegistrationLookupTransport transport,
    //         TrpSearchUnregisteredMemberApimMapper requestMapper,
    //         TrpSearchUnregisteredMemberApimResultMapper resultMapper) {
    //     return new ApimRegistrationLookupAdapter(
    //             transport,
    //             requestMapper,
    //             resultMapper);
    // }
}