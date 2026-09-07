package com.bct.ngtpa.apiservice.application.service.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class ExactPrecisionRendererTest {
  private final ExactPrecisionRenderer renderer = new ExactPrecisionRenderer();

  @Test
  void rendersGroupingTrailingZerosNegativeAndZeroExactly() {
    assertThat(renderer.render(new BigDecimal("5092583.02"), BigInteger.valueOf(2)))
        .isEqualTo("5,092,583.02");
    assertThat(renderer.render(new BigDecimal("100"), BigInteger.valueOf(2))).isEqualTo("100.00");
    assertThat(renderer.render(new BigDecimal("-1.2"), BigInteger.valueOf(2))).isEqualTo("-1.20");
    assertThat(renderer.render(BigDecimal.ZERO, BigInteger.valueOf(2))).isEqualTo("0.00");
  }

  @Test
  void rejectsAnyValueThatWouldRequireRounding() {
    assertThatThrownBy(() -> renderer.render(new BigDecimal("10.125"), BigInteger.valueOf(2)))
        .isInstanceOf(ArithmeticException.class);
  }
}
