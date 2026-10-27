package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.personalinformation.PersonalInformationController;
import com.bct.ngtpa.apiservice.adapter.in.web.response.UpdatePersonalInformationResponse;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class UpdatePersonalInformationControllerResultArrayArchitectureGuardTest {
  @Test
  void updateEndpointReturnsMonoOfEndpointEnvelopeNotMutationOfAccountList() {
    var method = Arrays.stream(PersonalInformationController.class.getDeclaredMethods())
        .filter(candidate -> candidate.getName().equals("update")).findFirst().orElseThrow();
    var mono = (ParameterizedType) method.getGenericReturnType();
    assertThat(mono.getRawType()).isEqualTo(Mono.class);
    assertThat(mono.getActualTypeArguments()[0]).isEqualTo(UpdatePersonalInformationResponse.class);
  }
}
