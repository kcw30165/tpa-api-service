package com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationSelectedTrust;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.RegistrationUsernameAvailabilityCommand;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class TrpChkCustIdApimMapperTest {

  @Test
  void mapsApplicationCommandToAdapterDtoWithoutFeDtoLeakage() {
    var command =
        new RegistrationUsernameAvailabilityCommand(
            new RegistrationSelectedTrust("TL", "ENV1"),
            "abc1@",
            "req-1",
            com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR);

    var request = new TrpChkCustIdApimMapper().toApimRequest(command);

    assertThat(request.customerId()).isEqualTo("abc1@");
    assertThat(request.getClass().getName())
        .startsWith("com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.");
    assertThat(request.getClass().getName()).doesNotContain("adapter.in.web");
    assertThat(componentNames(request.getClass()))
        .isNotEmpty()
        .doesNotContain("policyNo", "certNo", "password", "confirmPassword");
  }

  @Test
  void adapterRequestToStringRedactsCustomerId() {
    var command =
        new RegistrationUsernameAvailabilityCommand(
            new RegistrationSelectedTrust("TL", "ENV1"),
            "abc1@",
            "req-1",
            com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR);

    var request = new TrpChkCustIdApimMapper().toApimRequest(command);

    assertThat(request.toString()).doesNotContain("abc1@");
    assertThat(request.toString()).contains("customerId=<redacted>");
  }

  private static java.util.List<String> componentNames(Class<?> type) {
    return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
  }
}
