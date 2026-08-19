package com.bct.ngtpa.apiservice.adapter.out.apim.config;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpSearchUnregMemberApimMapper;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpSearchUnregMemberApimResultMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RegistrationLookupApimAdapterConfig {

  @Bean
  public TrpSearchUnregMemberApimMapper trpSearchUnregMemberApimMapper() {
    return new TrpSearchUnregMemberApimMapper();
  }

  @Bean
  public TrpSearchUnregMemberApimResultMapper trpSearchUnregMemberApimResultMapper() {
    return new TrpSearchUnregMemberApimResultMapper();
  }
}
