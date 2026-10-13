package com.bct.ngtpa.apiservice.adapter.out.apim.accountbalance;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.apim.accountbalance.dto.AccountBalanceCurrency;
import com.bct.ngtpa.apiservice.adapter.out.apim.accountbalance.dto.AccountBalanceDisplayOptions;
import com.bct.ngtpa.apiservice.adapter.out.apim.accountbalance.dto.AccountBalanceFundDetail;
import com.bct.ngtpa.apiservice.adapter.out.apim.accountbalance.dto.TrpGetMemberAccBalApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.FetchAccountBalanceCommand;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * Regression for the TRPGetMemberAccBal HTTP 503: TAS added {@code chin-fund-fact-sheet} to {@code
 * fundDtl}, which the strict production ObjectMapper (JacksonConfig, FAIL_ON_UNKNOWN_PROPERTIES
 * left on) rejected while the field was unmapped.
 */
class AccountBalanceChineseFactsheetContractTest {

  // Mirrors JacksonConfig#objectMapper.
  private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

  private static final String ENVELOPE =
      """
      {"response":{"err-message":"","data":[{
        "as-of-date":"2026-05-27",
        "displayOptions":[{"vestedBalanceVisible":true,"pendingSwitchInVisible":true,"fxRateVisible":true}],
        "dispSrc":[],
        "curr":[{"avai":["HKD"],"selected":"HKD"}],
        "fundDtl":[{"fund-code":"DBLF","fund-name":"Fund","chin-fund-name":"Fund ZH","fund-seq":60,
          "fund-price":535.9379,"fund-curr":"HKD","exchange-rate":1.0,
          "fund-fact-sheet":"https://example.test/en.pdf",
          "chin-fund-fact-sheet":"https://example.test/zh.pdf"}],
        "accBal":[]}]}}
      """;

  @Test
  void theStrictProductionMapperParsesAnEnvelopeCarryingChinFundFactSheet() throws Exception {
    JavaType type =
        mapper
            .getTypeFactory()
            .constructParametricType(
                ApimResponseEnvelope.class, TrpGetMemberAccBalApimDataItem.class);

    ApimResponseEnvelope<TrpGetMemberAccBalApimDataItem> envelope =
        mapper.readValue(ENVELOPE, type);

    var fund = envelope.getResponse().getData().getFirst().fundDetails().getFirst();
    assertThat(fund.fundFactsheet()).isEqualTo("https://example.test/en.pdf");
    assertThat(fund.chineseFundFactsheet()).isEqualTo("https://example.test/zh.pdf");
  }

  @Test
  void anAbsentChinFundFactSheetStaysNull() throws Exception {
    var fund =
        mapper.readValue(
            "{\"fund-code\":\"F\",\"fund-fact-sheet\":\"https://example.test/en.pdf\"}",
            AccountBalanceFundDetail.class);

    assertThat(fund.fundFactsheet()).isEqualTo("https://example.test/en.pdf");
    assertThat(fund.chineseFundFactsheet()).isNull();
  }

  @Test
  void theAdapterCarriesBothFactsheetsIntoTheRawFundDetail() {
    var item =
        new TrpGetMemberAccBalApimDataItem(
            LocalDate.of(2026, 5, 27),
            List.of(new AccountBalanceDisplayOptions(true, false, true)),
            List.of(new AccountBalanceCurrency(List.of("HKD"), "HKD")),
            List.of(),
            List.of(
                new AccountBalanceFundDetail(
                    "F1",
                    "Fund",
                    "Fund ZH",
                    1,
                    BigDecimal.ONE,
                    "HKD",
                    BigDecimal.ONE,
                    "https://example.test/en.pdf",
                    "https://example.test/zh.pdf")),
            List.of());
    var envelope = new ApimResponseEnvelope<>(new ApimResponseBody<>("", List.of(item)));
    var properties = new AccountBalanceApimProperties();
    properties.setPath("/ws/NGTPA/v1/TRPGetMemberAccBal");
    properties.validate();

    var raw =
        new ApimAccountBalanceSourceAdapter(executorReturning(envelope), properties)
            .fetchRawAccountBalance(
                new FetchAccountBalanceCommand(
                    "en",
                    null,
                    "DB",
                    "P",
                    "1",
                    "T",
                    "S",
                    "U",
                    ActorUserRole.ROLE_USER_MBR,
                    LocalDate.of(2026, 10, 13)))
            .block();

    var fund = raw.fundDetails().getFirst();
    assertThat(fund.fundFactsheet()).isEqualTo("https://example.test/en.pdf");
    assertThat(fund.chineseFundFactsheet()).isEqualTo("https://example.test/zh.pdf");
  }

  private static ApimExchangeExecutor executorReturning(
      ApimResponseEnvelope<TrpGetMemberAccBalApimDataItem> envelope) {
    return new ApimExchangeExecutor() {
      @Override
      @SuppressWarnings("unchecked")
      public <R, D> Mono<ApimResponseEnvelope<D>> postEnvelope(ApimExchangeRequest<R, D> request) {
        return Mono.just((ApimResponseEnvelope<D>) (ApimResponseEnvelope<?>) envelope);
      }
    };
  }
}
