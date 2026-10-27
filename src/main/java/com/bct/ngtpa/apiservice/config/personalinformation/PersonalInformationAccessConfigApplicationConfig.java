package com.bct.ngtpa.apiservice.config.personalinformation;

import com.bct.ngtpa.apiservice.application.port.in.GetPersonalInformationUseCase;
import com.bct.ngtpa.apiservice.application.usecase.personalinformation.PersonalInformationAccessConfigService;
import com.bct.ngtpa.apiservice.application.usecase.personalinformation.PersonalInformationConfigCacheService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires {@link PersonalInformationAccessConfigService}, the lookup behind the PUT field-access
 * check (design D-13). The service carries no Spring annotation (application layer stays
 * framework-free).
 *
 * <p>Both dependencies already exist as beans: the caching {@link GetPersonalInformationUseCase}
 * from {@code UseCaseConfig} and {@link PersonalInformationConfigCacheService} from {@code
 * PersonalInformationConfigCacheApplicationConfig}.
 */
@Configuration(proxyBeanMethods = false)
public class PersonalInformationAccessConfigApplicationConfig {

  @Bean
  PersonalInformationAccessConfigService personalInformationAccessConfigService(
      GetPersonalInformationUseCase getPersonalInformationUseCase,
      PersonalInformationConfigCacheService cache) {
    return new PersonalInformationAccessConfigService(getPersonalInformationUseCase, cache);
  }
}
