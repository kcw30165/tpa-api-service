package com.bct.ngtpa.apiservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.FetchAccountBalanceCommand;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.raw.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AccountBalanceSourcePortContractTest {
  @Test
  void carriesRawValidatedAccountBalanceDataWithoutPresentationTransformation() {
    var raw =
        new RawAccountBalanceData(
            new RawAccountBalanceDisplayOptions(true, false, true),
            new RawAccountBalanceCurrency(List.of("HKD"), "HKD"),
            List.of(new RawAccountBalanceDisplaySource("EE", "Member Account", "Member Account", "OE", "Member Account", "Member Account", 50)),
            List.of(new RawAccountBalanceFundDetail("DBLF", "Fund", "基金", 60, new BigDecimal("535.9379"), "HKD", new BigDecimal("1.0"), "not a url")),
            List.of(new RawAccountBalanceHolding("EE", "OE", "DBLF", new BigDecimal("1.250"), new BigDecimal("10.25"), null, null)));
    AccountBalanceSourcePort port = command -> Mono.just(raw);
    StepVerifier.create(port.fetchRawAccountBalance(null)).expectNext(raw).verifyComplete();
    assertThat(raw.fundDetails().getFirst().fundFactsheet()).isEqualTo("not a url");
    assertThat(raw.holdings().getFirst().vestedBalance()).isNull();
  }
}
