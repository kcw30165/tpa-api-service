package com.bct.ngtpa.apiservice.adapter.in.web.accountswitch;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import org.junit.jupiter.api.Test;

class AccountSwitchWebMapperTest {

  @Test
  void mapsAccountContextFieldsIntoResponse() {
    var account =
        new AccountContext(
            "acc_xzxMWFN5aVMVyOTThpAe4wN0kObWotWb",
            "AA",
            "1111111111111111111111111",
            "1",
            "BB",
            "CC",
            TermStatus.BLANK,
            null);

    var response = new AccountSwitchWebMapper().toResponse(account);

    assertThat(response.success()).isTrue();
    assertThat(response.status()).isEqualTo(ApiStatus.SUCCESS);
    assertThat(response.result().accountRef()).isEqualTo("acc_xzxMWFN5aVMVyOTThpAe4wN0kObWotWb");
    assertThat(response.result().policyNo()).isEqualTo("1111111111111111111111111");
    assertThat(response.result().certNo()).isEqualTo("1");
    assertThat(response.result().accountEnv()).isEqualTo("AA");
    assertThat(response.result().trust()).isEqualTo("BB");
    assertThat(response.result().schemeType()).isEqualTo("CC");
    assertThat(response.errors()).isEmpty();
  }
}
