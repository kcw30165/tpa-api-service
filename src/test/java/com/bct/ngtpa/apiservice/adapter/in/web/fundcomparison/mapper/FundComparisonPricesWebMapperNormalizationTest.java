package com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.request.FundComparisonPricesRequest;
import com.bct.ngtpa.apiservice.application.exception.FundComparisonRequestInvalidException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Fund Comparison M5.4: request normalization (09 section 9.5.4). */
class FundComparisonPricesWebMapperNormalizationTest {

  private final FundComparisonPricesWebMapper mapper =
      new FundComparisonPricesWebMapper(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

  private FundComparisonPricesRequest request(List<String> fundCodes, String timeRange) {
    return new FundComparisonPricesRequest(fundCodes, timeRange);
  }

  @Test
  void lowerCaseAndPaddedTimeRangesPassSyntaxValidationAndAreTrimmed() {
    assertThat(mapper.validate(request(List.of("FAEU"), " 10d ")).timeRange()).isEqualTo("10d");
    assertThat(mapper.validate(request(List.of("FAEU"), "ytd")).timeRange()).isEqualTo("ytd");
    assertThat(mapper.validate(request(List.of("FAEU"), " 3Y")).timeRange()).isEqualTo("3Y");
  }

  @ParameterizedTest
  @ValueSource(strings = {"10x", "0d", "ytdd", "d10", "1.5m"})
  void invalidTokensAreStillRejectedInAnyCase(String timeRange) {
    var invalid = request(List.of("FAEU"), timeRange);

    assertThatThrownBy(() -> mapper.validate(invalid))
        .isInstanceOf(FundComparisonRequestInvalidException.class);
  }

  @Test
  void fundCodesAreTrimmed() {
    assertThat(mapper.validate(request(List.of(" FAEU ", "FBAU "), "10D")).fundCodes())
        .containsExactly("FAEU", "FBAU");
  }

  @Test
  void duplicatesDifferingOnlyInCaseOrWhitespaceKeepTheFirstSpelling() {
    assertThat(mapper.validate(request(List.of("faeu", " FAEU ", "FBAU"), "10D")).fundCodes())
        .containsExactly("faeu", "FBAU");
  }

  @Test
  void theFiveFundLimitCountsCaseInsensitiveUniqueCodes() {
    var validated = mapper.validate(request(List.of("A", "B", "C", "D", "E", "a"), "10D"));

    assertThat(validated.fundCodes()).containsExactly("A", "B", "C", "D", "E");
  }
}
