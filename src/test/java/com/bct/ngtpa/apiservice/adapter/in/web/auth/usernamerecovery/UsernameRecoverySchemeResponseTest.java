package com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.dto.UsernameRecoverySchemeResponse;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Structural contract of the public scheme projection record.
 *
 * <p>schemeRef is accepted when null by the record (the Start flow never produces a null one),
 * and must never be disclosed by {@code toString()}.
 */
class UsernameRecoverySchemeResponseTest {

  @Test
  void schemeResponseExposesOnlyApprovedPublicFields() {
    assertThat(
            Arrays.stream(UsernameRecoverySchemeResponse.class.getRecordComponents())
                .map(RecordComponent::getName))
        .containsExactly("schemeRef", "accountEnv", "trustCode", "isRegistered");
  }

  @Test
  void toStringNeverDisclosesSchemeRef() {
    var response = new UsernameRecoverySchemeResponse("sch_opaque-1", "DB", "DBA", true);

    assertThat(response.toString()).contains("DB", "DBA", "true").doesNotContain("sch_opaque-1");
  }

  @Test
  void nullSchemeRefIsNotRejectedByTheRecord() {
    var response = new UsernameRecoverySchemeResponse(null, "DB", "DBA", false);

    assertThat(response.schemeRef()).isNull();
    assertThat(response.toString()).contains("null");
  }
}
