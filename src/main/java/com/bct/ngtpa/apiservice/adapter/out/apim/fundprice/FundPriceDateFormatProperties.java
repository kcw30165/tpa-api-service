package com.bct.ngtpa.apiservice.adapter.out.apim.fundprice;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Config-backed date formats for Fund Price.
 *
 * <p>{@code full-date}: FE input, the downstream {@code ref-date}/{@code from-date}/{@code
 * to-date} request fields, and the downstream {@code available-fund-price-dates} response field.
 * Per docs/design/9-fund-price/fund-price-date-validation-contract.md, available dates use this
 * SAME pattern and must not get their own property.
 *
 * <p>{@code month}: display-only year-month pattern for the export's Fund Price Date column when
 * the cached mode is MONTHLY. It is never used to parse FE input or build downstream requests.
 *
 * <p>Invalid configuration fails startup, matching the
 * AccountBalanceApimProperties/AccountBalanceApimValidationConfig precedent.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "fund-price.date-format")
public class FundPriceDateFormatProperties {

  private static final LocalDate ROUND_TRIP_PROBE = LocalDate.of(1999, 12, 31);

  private String fullDate = "dd/MM/uuuu";

  private String month = "MM/uuuu";

  public void validate() {
    validateFullDate();
    validateMonth();
  }

  private void validateFullDate() {
    if (fullDate == null || fullDate.isBlank()) {
      throw new IllegalStateException("fund-price.date-format.full-date must not be blank");
    }
    fullDate = fullDate.trim();
    // Fail fast on an invalid pattern rather than at first use. ofPattern (via
    // fullDateFormatter) rejects unknown pattern letters; the strict round-trip below
    // rejects patterns that format but cannot parse back -- notably "yyyy" (year-of-era)
    // without an era field, which under ResolverStyle.STRICT never resolves a LocalDate --
    // and reduced two-digit years that silently shift the century. Use "uuuu".
    DateTimeFormatter strict = fullDateFormatter();
    LocalDate parsed;
    try {
      parsed = LocalDate.parse(ROUND_TRIP_PROBE.format(strict), strict);
    } catch (DateTimeException exception) {
      throw new IllegalStateException(
          "fund-price.date-format.full-date '" + fullDate + "' cannot be parsed back under"
              + " strict resolution; use 'uuuu' for the year instead of 'yyyy'",
          exception);
    }
    if (!ROUND_TRIP_PROBE.equals(parsed)) {
      throw new IllegalStateException(
          "fund-price.date-format.full-date '" + fullDate + "' does not round-trip a full date");
    }
  }

  private void validateMonth() {
    if (month == null || month.isBlank()) {
      throw new IllegalStateException("fund-price.date-format.month must not be blank");
    }
    month = month.trim();
    // Same strict round trip as full-date, against YearMonth. The pattern must not render
    // the day of month: two dates in the same month must produce identical text.
    DateTimeFormatter strict = monthFormatter();
    YearMonth parsed;
    try {
      String text = ROUND_TRIP_PROBE.format(strict);
      if (!text.equals(ROUND_TRIP_PROBE.withDayOfMonth(1).format(strict))) {
        throw new IllegalStateException(
            "fund-price.date-format.month '" + month + "' must not include the day of month");
      }
      parsed = YearMonth.parse(text, strict);
    } catch (DateTimeException exception) {
      throw new IllegalStateException(
          "fund-price.date-format.month '" + month + "' cannot be parsed back under strict"
              + " resolution; use 'uuuu' for the year instead of 'yyyy'",
          exception);
    }
    if (!YearMonth.from(ROUND_TRIP_PROBE).equals(parsed)) {
      throw new IllegalStateException(
          "fund-price.date-format.month '" + month + "' does not round-trip a year and month");
    }
  }

  public DateTimeFormatter fullDateFormatter() {
    return DateTimeFormatter.ofPattern(fullDate).withResolverStyle(ResolverStyle.STRICT);
  }

  public DateTimeFormatter monthFormatter() {
    return DateTimeFormatter.ofPattern(month).withResolverStyle(ResolverStyle.STRICT);
  }
}
