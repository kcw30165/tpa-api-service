package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationDownstreamRejectionLoggingContractTest {

  @Test
  void registrationLookupBusinessRejectionProducesStructuredFailureEvent() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/registration/ApimRegistrationLookupAdapter.java"));

    assertThat(source)
        .contains("RegistrationLookupStatus.NOT_ELIGIBLE")
        .contains("event.registration.lookup.downstream_rejection")
        .contains("err.tpa.member.not.found")
        .contains("START_VERIFICATION_FAILED")
        .contains("failureEventPort.publish")
        .contains("422")
        .contains("false");
  }
}
