package com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

final class TrpGetCIMDtlApimMapper {

  private static final DateTimeFormatter DATE_FORMATTER =
      DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
  private static final DateTimeFormatter TIME_FORMATTER =
      DateTimeFormatter.ofPattern("HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);

  static String formatDate(LocalDate value) {
    return DATE_FORMATTER.format(value);
  }

  static LocalDate parseDate(String value) {
    if (value == null) {
      throw new InvestmentChoiceMalformedResponseException();
    }
    try {
      return LocalDate.parse(value, DATE_FORMATTER);
    } catch (DateTimeParseException exception) {
      throw new InvestmentChoiceMalformedResponseException();
    }
  }

  static LocalTime parseTime(String value) {
    if (value == null) {
      throw new InvestmentChoiceMalformedResponseException();
    }
    try {
      return LocalTime.parse(value, TIME_FORMATTER);
    } catch (DateTimeParseException exception) {
      throw new InvestmentChoiceMalformedResponseException();
    }
  }
}
