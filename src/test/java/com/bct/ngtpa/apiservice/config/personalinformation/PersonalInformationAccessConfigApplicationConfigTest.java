package com.bct.ngtpa.apiservice.config.personalinformation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bct.ngtpa.apiservice.application.port.in.GetPersonalInformationUseCase;
import com.bct.ngtpa.apiservice.application.usecase.personalinformation.PersonalInformationConfigCacheService;
import org.junit.jupiter.api.Test;

class PersonalInformationAccessConfigApplicationConfigTest {

  @Test
  void wiresTheAccessConfigServiceFromItsCollaborators() {
    var service =
        new PersonalInformationAccessConfigApplicationConfig()
            .personalInformationAccessConfigService(
                mock(GetPersonalInformationUseCase.class),
                mock(PersonalInformationConfigCacheService.class));

    assertThat(service).isNotNull();
  }
}
