package com.bct.ngtpa.apiservice.adapter.in.web.fundprice.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Fund Price XLSX export settings, sourced from Config Service with these values as {@code
 * application.yml} defaults (precedence: Config Service -> application.yml -> these code defaults).
 *
 * <p>Header structure mirrors {@code ContributionSummaryProperties} exactly: a nested {@code
 * Headers} group with per-column-set inner classes ({@code UnitPrice}, {@code BidOffer}) for the
 * two mutually exclusive column sets, per
 * docs/capabilities/config-service-startup-loader/implementation-plan.md section 6 and
 * docs/design/9-fund-price/fund-price-excel-export-contract.md.
 *
 * <p>Header cells that carry no language-bearing content concatenate the English and Chinese label
 * directly, with no separator -- this is baked into the default string values themselves, not
 * computed. {@code Fund Name} and {@code 基金名稱} remain two distinct columns and are never
 * concatenated. The space before the Chinese label in {@code unit-price.currency} is deliberate and
 * must not be "normalized" to match the other headers.
 *
 * <p>{@code sheet-names} maps each downstream fund-price-type to the workbook sheet name. The types
 * contain a space, so YAML keys must be bracketed ({@code "[BIDOFFER PRICE]"}) or Spring Boot
 * strips the space when binding. {@link #validate()} runs at startup (via the exporter bean): both
 * supported types must be mapped to a non-blank Excel-legal sheet name.
 */
@ConfigurationProperties(prefix = "fund-price.export")
@Getter
@Setter
public class FundPriceExportProperties {

  /** Fund-price-type values TRPGetFundPriceMode may return; each needs a sheet name. */
  public static final List<String> REQUIRED_FUND_PRICE_TYPES =
      List.of("BIDOFFER PRICE", "UNIT PRICE");

  private static final int MAX_SHEET_NAME_LENGTH = 31;
  private static final String INVALID_SHEET_NAME_CHARACTERS = "[]:*?/\\";

  private final Headers headers = new Headers();

  private Map<String, String> sheetNames = defaultSheetNames();

  private static Map<String, String> defaultSheetNames() {
    Map<String, String> defaults = new LinkedHashMap<>();
    defaults.put("BIDOFFER PRICE", "BIDOFFER PRICE");
    defaults.put("UNIT PRICE", "UNIT PRICE");
    return defaults;
  }

  /** Fails startup on a missing, blank, too-long or Excel-illegal sheet name. */
  public void validate() {
    if (sheetNames == null || sheetNames.isEmpty()) {
      throw new IllegalStateException("fund-price.export.sheet-names must not be empty");
    }
    for (String type : REQUIRED_FUND_PRICE_TYPES) {
      if (!sheetNames.containsKey(type)) {
        throw new IllegalStateException(
            "fund-price.export.sheet-names is missing fund-price-type '" + type + "'");
      }
    }
    Map<String, String> validated = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : sheetNames.entrySet()) {
      validated.put(entry.getKey(), validSheetName(entry.getKey(), entry.getValue()));
    }
    sheetNames = validated;
  }

  /** Sheet name for the fund-price-type, or {@code null} when the type is not configured. */
  public String sheetName(String fundPriceType) {
    return fundPriceType == null ? null : sheetNames.get(fundPriceType);
  }

  private static String validSheetName(String type, String name) {
    String key = "fund-price.export.sheet-names[" + type + "]";
    if (name == null || name.isBlank()) {
      throw new IllegalStateException(key + " must not be blank");
    }
    String trimmed = name.trim();
    if (trimmed.length() > MAX_SHEET_NAME_LENGTH) {
      throw new IllegalStateException(
          key + " must not exceed " + MAX_SHEET_NAME_LENGTH + " characters");
    }
    for (char c : trimmed.toCharArray()) {
      if (INVALID_SHEET_NAME_CHARACTERS.indexOf(c) >= 0) {
        throw new IllegalStateException(
            key + " must not contain any of " + INVALID_SHEET_NAME_CHARACTERS);
      }
    }
    return trimmed;
  }

  @Getter
  @Setter
  public static class Headers {

    private String fundPriceDate = "Fund Price Date\u57fa\u91d1\u50f9\u683c\u65e5\u671f";
    private String fundName = "Fund Name";
    private String chineseFundName = "\u57fa\u91d1\u540d\u7a31";

    private final UnitPrice unitPrice = new UnitPrice();
    private final BidOffer bidOffer = new BidOffer();

    @Getter
    @Setter
    public static class UnitPrice {
      private String currency = "Unit Price Currency \u55ae\u4f4d\u8ca8\u5e63";
      private String price = "Unit Price\u55ae\u4f4d\u50f9\u683c";
    }

    @Getter
    @Setter
    public static class BidOffer {
      private String bidCurrency = "Bid Price Currency\u8cb7\u5165\u55ae\u4f4d\u8ca8\u5e63";
      private String bidPrice = "Bid Unit Price\u55ae\u4f4d\u50f9\u683c";
      private String offerCurrency = "Offer Price Currency\u8ce3\u51fa\u55ae\u4f4d\u8ca8\u5e63";
      private String offerPrice = "Offer Price\u55ae\u4f4d\u50f9\u683c";
    }
  }
}
