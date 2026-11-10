package com.bct.ngtpa.apiservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupResult;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class RegistrationLookupPortContractShapeTest {

  @Test
  void registrationLookupPortExposesSearchOperation() throws Exception {
    Method method =
        RegistrationLookupPort.class.getMethod("searchMember", RegistrationLookupCommand.class);

    assertThat(method.getReturnType()).isEqualTo(Mono.class);
    assertThat(method.getGenericReturnType().getTypeName())
        .contains(RegistrationLookupResult.class.getSimpleName());
  }

  @Test
  void registrationLookupPortExposesSynchronousFailureClassification() throws Exception {
    Method method =
        RegistrationLookupPort.class.getMethod("classifySearchMemberFailure", Throwable.class);

    assertThat(method.getReturnType()).isEqualTo(FailureOrigin.class);
  }
}
