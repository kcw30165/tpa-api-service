package com.bct.ngtpa.apiservice.adapter.in.web.fundprice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Fund Price XLSX export header labels, sourced from Config Service with these values as {@code
 * application.yml} defaults (precedence: Config Service -> application.yml -> these code defaults).
 *
 * <p>Structure mirrors {@code ContributionSummaryProperties} exactly: a nested {@code Headers}
 * group with per-column-set inner classes ({@code UnitPrice}, {@code BidOffer}) for the two
 * mutually exclusive column sets, per
 * docs/capabilities/config-service-startup-loader/implementation-plan.md section 6 and
 * docs/design/9-fund-price/fund-price-excel-export-contract.md.
 *
 * <p>Header cells that carry no language-bearing content concatenate the English and Chinese label
 * directly, with no separator -- this is baked into the default string values themselves, not
 * computed. {@code Fund Name} and {@code 基金名稱} remain two distinct columns and are never
 * concatenated. The space before the Chinese label in {@code unit-price.currency} is deliberate and
 * must not be "normalized" to match the other headers.
 */
@ConfigurationProperties(prefix = "fund-price.export")
@Getter
@Setter
public class FundPriceExportProperties {

  private final Headers headers = new Headers();

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
