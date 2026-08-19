package com.bct.ngtpa.apiservice.adapter.out.apim.credential;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import org.junit.jupiter.api.Test;

class ApimCredentialResolutionContextTest {
  @Test
  void contextHasExactlyTwoComponents() {
    RecordComponent[] comps = ApimCredentialResolutionContext.class.getRecordComponents();
    assertThat(comps).hasSize(2);
    assertThat(comps[0].getName()).isEqualTo("actorUserRole");
    assertThat(comps[1].getName()).isEqualTo("accountEnv");
  }
}
