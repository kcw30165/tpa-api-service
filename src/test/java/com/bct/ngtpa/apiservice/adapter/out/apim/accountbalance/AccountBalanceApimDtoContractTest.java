package com.bct.ngtpa.apiservice.adapter.out.apim.accountbalance;

import static org.assertj.core.api.Assertions.*;

import com.bct.ngtpa.apiservice.adapter.out.apim.accountbalance.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccountBalanceApimDtoContractTest {
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void serializesExactInitialLoadAndCurrencyChangeRequests() throws Exception {
    var initial =
        new TrpGetMemberAccBalApimRequest(
            "db230", "role_user_mbr", "00000008802", "0230", "31/08/2026", null);
    assertThat(json.writeValueAsString(initial))
        .isEqualTo(
            "{\"login-user-id\":\"db230\",\"login-user-role\":\"role_user_mbr\",\"policy-no\":\"00000008802\",\"cert-no\":\"0230\",\"ref-date\":\"31/08/2026\",\"curr\":\"\"}");
    var changed =
        new TrpGetMemberAccBalApimRequest(
            "db230", "role_user_mbr", "00000008802", "0230", "31/08/2026", "USD");
    assertThat(json.readTree(json.writeValueAsString(changed)).path("curr").asText())
        .isEqualTo("USD");
  }

  @Test
  void keepsCertificateNumberTextualSoConfiguredEncryptionCanTransformIt() throws Exception {
    var request =
        new TrpGetMemberAccBalApimRequest(
            "db12", "role_user_mbr", "policy", "0055", "31/08/2026", "");

    var node = json.valueToTree(request);

    assertThat(node.path("cert-no").isTextual()).isTrue();
    assertThat(node.path("cert-no").asText()).isEqualTo("0055");
  }

  @Test
  void deserializesCompleteRawResponseWithoutTransformation() throws Exception {
    var item = json.readValue(sample(), TrpGetMemberAccBalApimDataItem.class);
    assertThat(item.displayOptions().getFirst().vestedBalanceVisible()).isTrue();
    assertThat(item.currencies().getFirst().available()).containsExactly("HKD");
    assertThat(item.displaySources().getFirst().displaySource()).isEqualTo("EE");
    assertThat(item.displaySources().getFirst().sourceChineseDescription())
        .isEqualTo("Member Account");
    assertThat(item.fundDetails().getFirst().fundPrice()).isEqualByComparingTo("535.9379");
    assertThat(item.fundDetails().getFirst().exchangeRate()).isEqualByComparingTo("1.0");
    assertThat(item.fundDetails().getFirst().fundFactsheet()).isEmpty();
    assertThat(item.accountBalances().getFirst().vestedBalance())
        .isEqualByComparingTo("1467156.68");
    assertThat(item.accountBalances().getFirst().transitAmount()).isEqualByComparingTo("0.0");
  }

  @Test
  void preservesBlankDescriptionsSentinelsAndRawFactsheet() throws Exception {
    String raw =
        "{\"displayOptions\":[{\"vestedBalanceVisible\":true,\"pendingSwitchInVisible\":false,\"fxRateVisible\":true}],\"curr\":[{\"avai\":[\"HKD\"],\"selected\":\"HKD\"}],\"dispSrc\":[{\"disp-src\":\"EE\",\"src-desc\":\"\",\"src-chin-desc\":\"\",\"sub-disp-src\":\"OE\",\"sub-src-desc\":\"\",\"sub-src-chin-desc\":\"\",\"seq\":-1}],\"fundDtl\":[{\"fund-code\":\"F\",\"fund-name\":\"\",\"chin-fund-name\":\"\",\"fund-seq\":-1,\"fund-price\":-999.99,\"fund-curr\":\"HKD\",\"exchange-rate\":-1,\"fund-fact-sheet\":\"not a url\"}],\"accBal\":[{\"disp-src\":\"EE\",\"sub-disp-src\":\"OE\",\"fund-code\":\"F\",\"unit\":-1,\"nav\":-999.99,\"vested-bal\":null,\"transit-amt\":null}]}";
    var item = json.readValue(raw, TrpGetMemberAccBalApimDataItem.class);
    assertThat(item.displaySources().getFirst().sourceDescription()).isEmpty();
    assertThat(item.fundDetails().getFirst().fundPrice())
        .isEqualByComparingTo(new BigDecimal("-999.99"));
    assertThat(item.fundDetails().getFirst().fundFactsheet()).isEqualTo("not a url");
    assertThat(item.accountBalances().getFirst().vestedBalance()).isNull();
  }

  @Test
  void rejectsInvalidSingletonArraysAndNullVariableArrays() {
    var options = new AccountBalanceDisplayOptions(true, false, true);
    var currency = new AccountBalanceCurrency(List.of("HKD"), "HKD");
    assertThatThrownBy(
            () ->
                AccountBalanceApimContentValidator.requireSingleData(
                    List.of(
                        new TrpGetMemberAccBalApimDataItem(
                            List.of(), List.of(currency), List.of(), List.of(), List.of()))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("displayOptions");
    assertThatThrownBy(
            () ->
                AccountBalanceApimContentValidator.requireSingleData(
                    List.of(
                        new TrpGetMemberAccBalApimDataItem(
                            List.of(options, options),
                            List.of(currency),
                            List.of(),
                            List.of(),
                            List.of()))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("displayOptions");
    assertThatThrownBy(
            () ->
                AccountBalanceApimContentValidator.requireSingleData(
                    List.of(
                        new TrpGetMemberAccBalApimDataItem(
                            List.of(options), List.of(), List.of(), List.of(), List.of()))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("curr");
    assertThatThrownBy(
            () ->
                AccountBalanceApimContentValidator.requireSingleData(
                    List.of(
                        new TrpGetMemberAccBalApimDataItem(
                            List.of(options), List.of(currency), null, List.of(), List.of()))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("arrays");
  }

  @Test
  void requiresExactlyOneCompleteDataItem() {
    assertThatThrownBy(() -> AccountBalanceApimContentValidator.requireSingleData(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> AccountBalanceApimContentValidator.requireSingleData(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> AccountBalanceApimContentValidator.requireSingleData(List.of(item(), item())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                AccountBalanceApimContentValidator.requireSingleData(
                    java.util.Arrays.asList((TrpGetMemberAccBalApimDataItem) null)))
        .isInstanceOf(IllegalArgumentException.class);
    var completeItem = item();
    assertThat(AccountBalanceApimContentValidator.requireSingleData(List.of(completeItem)))
        .isSameAs(completeItem);
  }

  private static TrpGetMemberAccBalApimDataItem item() {
    return new TrpGetMemberAccBalApimDataItem(
        List.of(new AccountBalanceDisplayOptions(true, false, true)),
        List.of(new AccountBalanceCurrency(List.of("HKD"), "HKD")),
        List.of(),
        List.of(),
        List.of());
  }

  private static String sample() {
    return "{\"displayOptions\":[{\"vestedBalanceVisible\":true,\"pendingSwitchInVisible\":false,\"fxRateVisible\":true}],\"curr\":[{\"avai\":[\"HKD\"],\"selected\":\"HKD\"}],\"dispSrc\":[{\"disp-src\":\"EE\",\"src-desc\":\"Member Account\",\"src-chin-desc\":\"Member Account\",\"sub-disp-src\":\"OE\",\"sub-src-desc\":\"Member Account\",\"sub-src-chin-desc\":\"Member Account\",\"seq\":50}],\"fundDtl\":[{\"fund-code\":\"DBLF\",\"fund-name\":\"Fidelity Balanced Fund\",\"chin-fund-name\":\"Fidelity Balanced Fund\",\"fund-seq\":60,\"fund-price\":535.9379,\"fund-curr\":\"HKD\",\"exchange-rate\":1.0,\"fund-fact-sheet\":\"\"}],\"accBal\":[{\"disp-src\":\"EE\",\"sub-disp-src\":\"OE\",\"fund-code\":\"DGLE\",\"unit\":9096.201,\"nav\":3415434.27,\"vested-bal\":1467156.68,\"transit-amt\":0.0}]}";
  }
}
