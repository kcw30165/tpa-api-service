package com.bct.ngtpa.apiservice.adapter.out.apim.accountgainloss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AccountGainLossApimConfigurationTest {
  @Test
  void acceptsConfiguredPath() {
    var properties = new AccountGainLossApimProperties();
    properties.setPath("/ws/NGTPA/v1/TRPGetInvestGL");
    assertThat(properties.getPath()).isEqualTo("/ws/NGTPA/v1/TRPGetInvestGL");
  }

  @Test
  void rejectsBlankPath() {
    var properties = new AccountGainLossApimProperties();
    assertThatThrownBy(properties::getPath)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("apim.account-gain-loss.path");
  }
}
