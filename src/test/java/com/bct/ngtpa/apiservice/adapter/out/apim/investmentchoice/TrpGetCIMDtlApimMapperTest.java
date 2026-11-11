package com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrpGetCIMDtlApimMapperTest {

  @Test
  void parsesStrictDateAndTimeValues() {
    assertThat(TrpGetCIMDtlApimMapper.parseDate("05/11/2026")).isEqualTo(LocalDate.of(2026, 11, 5));
    assertThat(TrpGetCIMDtlApimMapper.parseTime("17:04:14")).isEqualTo(LocalTime.of(17, 4, 14));
  }

  @Test
  void rejectsMalformedDatesWithTheFeatureLocalSignal() {
    for (String value : List.of("2026-11-05", "31/02/2026", "5/11/2026", "")) {
      assertThatThrownBy(() -> TrpGetCIMDtlApimMapper.parseDate(value))
          .isInstanceOf(InvestmentChoiceMalformedResponseException.class);
    }

    assertThatThrownBy(() -> TrpGetCIMDtlApimMapper.parseDate(null))
        .isInstanceOf(InvestmentChoiceMalformedResponseException.class);
  }

  @Test
  void rejectsMalformedTimesWithTheFeatureLocalSignal() {
    for (String value : List.of("24:00:00", "17:04", "")) {
      assertThatThrownBy(() -> TrpGetCIMDtlApimMapper.parseTime(value))
          .isInstanceOf(InvestmentChoiceMalformedResponseException.class);
    }

    assertThatThrownBy(() -> TrpGetCIMDtlApimMapper.parseTime(null))
        .isInstanceOf(InvestmentChoiceMalformedResponseException.class);
  }
}
