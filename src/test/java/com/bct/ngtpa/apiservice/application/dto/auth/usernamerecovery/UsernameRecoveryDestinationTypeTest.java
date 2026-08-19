package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UsernameRecoveryDestinationTypeTest {

  @Test
  void exposesEmailAndMobileChannels() {
    assertThat(UsernameRecoveryDestinationType.values())
        .containsExactly(
            UsernameRecoveryDestinationType.EMAIL, UsernameRecoveryDestinationType.MOBILE);
  }

  @Test
  void enumNamesAreSafePublicValues() {
    assertThat(UsernameRecoveryDestinationType.EMAIL.name()).isEqualTo("EMAIL");
    assertThat(UsernameRecoveryDestinationType.MOBILE.name()).isEqualTo("MOBILE");
  }
}
