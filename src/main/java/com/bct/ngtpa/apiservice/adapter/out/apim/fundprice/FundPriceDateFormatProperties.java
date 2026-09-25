package com.bct.ngtpa.apiservice.adapter.out.apim.fundprice;

import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Config-backed full-date format for Fund Price FE input, the downstream {@code ref-date}/ {@code
 * from-date}/{@code to-date} request fields, and the downstream {@code available-fund-price-dates}
 * response field.
 *
 * <p>Per docs/design/9-fund-price/fund-price-date-validation-contract.md: "The format patterns must
 * not be embedded as operational Java literals... Available dates returned by TRPGetFundPriceMode
 * use the SAME configured full-date format... It is NOT an independently configured pattern and
 * must not be given its own property." Invalid configuration fails startup, matching the
 * AccountBalanceApimProperties/AccountBalanceApimValidationConfig precedent.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "fund-price.date-format")
public class FundPriceDateFormatProperties {

  private String fullDate = "dd/MM/yyyy";

  public void validate() {
    if (fullDate == null || fullDate.isBlank()) {
      throw new IllegalStateException("fund-price.date-format.full-date must not be blank");
    }
    fullDate = fullDate.trim();
    // Fail fast on an invalid pattern rather than at first use.
    DateTimeFormatter.ofPattern(fullDate);
  }

  public DateTimeFormatter fullDateFormatter() {
    return DateTimeFormatter.ofPattern(fullDate).withResolverStyle(ResolverStyle.STRICT);
  }
}
