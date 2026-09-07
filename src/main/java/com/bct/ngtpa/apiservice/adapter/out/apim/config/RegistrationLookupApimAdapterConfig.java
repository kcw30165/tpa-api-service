package com.bct.ngtpa.apiservice.adapter.out.apim.config;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpSearchMemberApimMapper;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpSearchMemberApimResultMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RegistrationLookupApimAdapterConfig {

  @Bean
  public TrpSearchMemberApimMapper trpSearchUnregMemberApimMapper() {
    return new TrpSearchMemberApimMapper();
  }

  @Bean
  public TrpSearchMemberApimResultMapper trpSearchUnregMemberApimResultMapper() {
    return new TrpSearchMemberApimResultMapper();
  }
}
