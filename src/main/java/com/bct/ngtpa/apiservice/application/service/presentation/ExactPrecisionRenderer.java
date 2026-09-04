package com.bct.ngtpa.apiservice.application.service.presentation;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

public final class ExactPrecisionRenderer {
  public String render(BigDecimal amount, BigInteger precision) {
    if (amount == null
        || precision == null
        || precision.signum() < 0
        || precision.bitLength() > 31) {
      throw new IllegalArgumentException("amount and precision must be valid");
    }
    int scale = precision.intValueExact();
    BigDecimal exact = amount.setScale(scale, RoundingMode.UNNECESSARY);
    String plain = exact.toPlainString();
    int dot = plain.indexOf('.');
    String integer = dot < 0 ? plain : plain.substring(0, dot);
    String fraction = dot < 0 ? "" : plain.substring(dot);
    boolean negative = integer.startsWith("-");
    String digits = negative ? integer.substring(1) : integer;
    StringBuilder grouped = new StringBuilder();
    for (int i = 0; i < digits.length(); i++) {
      if (i > 0 && (digits.length() - i) % 3 == 0) {
        grouped.append(',');
      }
      grouped.append(digits.charAt(i));
    }
    return (negative ? "-" : "") + grouped + fraction;
  }
}
