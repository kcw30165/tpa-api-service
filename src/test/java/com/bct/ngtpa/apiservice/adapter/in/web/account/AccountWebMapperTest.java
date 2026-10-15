package com.bct.ngtpa.apiservice.adapter.in.web.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccountWebMapperTest {

  @Test
  void mapsSingleAccountContextIntoResultDataResponse() {
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

    var response = new AccountWebMapper().toAccountResponse(account);

    assertThat(response.success()).isTrue();
    assertThat(response.status()).isEqualTo(ApiStatus.SUCCESS);
    assertThat(response.result().data().accountRef())
        .isEqualTo("acc_xzxMWFN5aVMVyOTThpAe4wN0kObWotWb");
    assertThat(response.result().data().policyNo()).isEqualTo("1111111111111111111111111");
    assertThat(response.result().data().certNo()).isEqualTo("1");
    assertThat(response.result().data().accountEnv()).isEqualTo("AA");
    assertThat(response.result().data().trustCode()).isEqualTo("BB");
    assertThat(response.result().data().schemeType()).isEqualTo("CC");
    assertThat(response.errors()).isEmpty();
  }

  @Test
  void mapsAccountListIntoResultDataAccountsResponse() {
    var a1 =
        new AccountContext(
            "acc_CEoNbrhgjmYgu2YgrdoqfB8psucGR7-X",
            "TB",
            "00000000112",
            "0582",
            "HKBU",
            "OE",
            TermStatus.BLANK,
            null);
    var a2 =
        new AccountContext(
            "acc_ibSsUgtP8zeG8AxixnOkUkt3GZQ6v_Ce",
            "TB",
            "00000004203",
            "0186",
            "MSIG",
            "OE",
            TermStatus.BLANK,
            null);

    var response = new AccountWebMapper().toAccountsResponse(List.of(a1, a2));

    assertThat(response.success()).isTrue();
    assertThat(response.status()).isEqualTo(ApiStatus.SUCCESS);
    assertThat(response.result().data().accounts()).hasSize(2);
    assertThat(response.result().data().accounts().get(0).accountRef())
        .isEqualTo("acc_CEoNbrhgjmYgu2YgrdoqfB8psucGR7-X");
    assertThat(response.result().data().accounts().get(0).trustCode()).isEqualTo("HKBU");
    assertThat(response.result().data().accounts().get(1).trustCode()).isEqualTo("MSIG");
  }
}
