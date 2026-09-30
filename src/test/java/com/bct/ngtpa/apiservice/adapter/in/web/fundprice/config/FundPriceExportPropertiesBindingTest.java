package com.bct.ngtpa.apiservice.adapter.in.web.fundprice.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;

/**
 * Fail-first: FundPriceExportProperties does not exist yet.
 *
 * <p>Follows the {@code ConfigServicePropertiesBindingTest} pattern exactly: {@link
 * ApplicationContextRunner} + {@link YamlPropertySourceLoader} + {@code addLast}, with default /
 * override / absent cases -- and the {@code ContributionSummaryProperties} nested
 * {@code @Getter}/{@code @Setter} inner-class shape for bilingual header groups.
 *
 * <p>Pins the exact 9 leaf keys and their kebab-case paths from the implementation plan
 * (docs/capabilities/config-service-startup-loader/implementation-plan.md, section 6), including
 * the deliberately-preserved space in {@code "Unit Price Currency 單位貨幣"} that must NOT be
 * normalized.
 */
class FundPriceExportPropertiesBindingTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

  private ApplicationContextRunner withYaml(String yaml) {
    return contextRunner.withInitializer(
        context -> {
          var resource = new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8));
          try {
            var propertySources =
                new YamlPropertySourceLoader().load("fundPriceExportPropertiesTest", resource);
            propertySources.forEach(
                source -> context.getEnvironment().getPropertySources().addLast(source));
          } catch (java.io.IOException ex) {
            throw new IllegalStateException("Failed to load YAML", ex);
          }
        });
  }

  // ---- defaults --------------------------------------------------------------

  @Test
  void defaultFundPriceDateHeaderIsBilingual() {
    contextRunner.run(
        context -> {
          var props = context.getBean(FundPriceExportProperties.class);
          assertEquals("Fund Price Date基金價格日期", props.getHeaders().getFundPriceDate());
        });
  }

  @Test
  void defaultFundNameHeaderIsEnglishOnly() {
    contextRunner.run(
        context -> {
          var props = context.getBean(FundPriceExportProperties.class);
          assertEquals("Fund Name", props.getHeaders().getFundName());
        });
  }

  @Test
  void defaultChineseFundNameHeaderIsChineseOnly() {
    contextRunner.run(
        context -> {
          var props = context.getBean(FundPriceExportProperties.class);
          assertEquals("\u57fa\u91d1\u540d\u7a31", props.getHeaders().getChineseFundName());
        });
  }

  @Test
  void defaultUnitPriceCurrencyHeaderPreservesTheSpaceBeforeChinese() {
    contextRunner.run(
        context -> {
          var props = context.getBean(FundPriceExportProperties.class);
          // Deliberate: a space before the Chinese label here, unlike the bid/offer headers.
          // Must not be "normalized" away.
          assertEquals(
              "Unit Price Currency \u55ae\u4f4d\u8ca8\u5e63",
              props.getHeaders().getUnitPrice().getCurrency());
        });
  }

  @Test
  void defaultUnitPricePriceHeader() {
    contextRunner.run(
        context -> {
          var props = context.getBean(FundPriceExportProperties.class);
          assertEquals(
              "Unit Price\u55ae\u4f4d\u50f9\u683c", props.getHeaders().getUnitPrice().getPrice());
        });
  }

  @Test
  void defaultBidOfferBidCurrencyHeader() {
    contextRunner.run(
        context -> {
          var props = context.getBean(FundPriceExportProperties.class);
          assertEquals(
              "Bid Price Currency\u8cb7\u5165\u55ae\u4f4d\u8ca8\u5e63",
              props.getHeaders().getBidOffer().getBidCurrency());
        });
  }

  @Test
  void defaultBidOfferBidPriceHeader() {
    contextRunner.run(
        context -> {
          var props = context.getBean(FundPriceExportProperties.class);
          assertEquals(
              "Bid Unit Price\u55ae\u4f4d\u50f9\u683c",
              props.getHeaders().getBidOffer().getBidPrice());
        });
  }

  @Test
  void defaultBidOfferOfferCurrencyHeader() {
    contextRunner.run(
        context -> {
          var props = context.getBean(FundPriceExportProperties.class);
          assertEquals(
              "Offer Price Currency\u8ce3\u51fa\u55ae\u4f4d\u8ca8\u5e63",
              props.getHeaders().getBidOffer().getOfferCurrency());
        });
  }

  @Test
  void defaultBidOfferOfferPriceHeader() {
    contextRunner.run(
        context -> {
          var props = context.getBean(FundPriceExportProperties.class);
          assertEquals(
              "Offer Price\u55ae\u4f4d\u50f9\u683c",
              props.getHeaders().getBidOffer().getOfferPrice());
        });
  }

  // ---- overrides (simulating a Config Service value winning over the default) ----

  @Test
  void overridesFundPriceDateHeaderViaKebabCasePath() {
    withYaml(
            """
            fund-price:
              export:
                headers:
                  fund-price-date: "Overridden Date Header"
            """)
        .run(
            context -> {
              var props = context.getBean(FundPriceExportProperties.class);
              assertEquals("Overridden Date Header", props.getHeaders().getFundPriceDate());
            });
  }

  @Test
  void overridesNestedUnitPriceGroupIndependently() {
    withYaml(
            """
            fund-price:
              export:
                headers:
                  unit-price:
                    currency: "Overridden Unit Currency"
                    price: "Overridden Unit Price"
            """)
        .run(
            context -> {
              var props = context.getBean(FundPriceExportProperties.class);
              assertEquals(
                  "Overridden Unit Currency", props.getHeaders().getUnitPrice().getCurrency());
              assertEquals("Overridden Unit Price", props.getHeaders().getUnitPrice().getPrice());
              // Sibling group must be untouched by the unit-price override.
              assertEquals(
                  "Bid Price Currency\u8cb7\u5165\u55ae\u4f4d\u8ca8\u5e63",
                  props.getHeaders().getBidOffer().getBidCurrency());
            });
  }

  @Test
  void overridesNestedBidOfferGroupIndependently() {
    withYaml(
            """
            fund-price:
              export:
                headers:
                  bid-offer:
                    bid-currency: "Overridden Bid Currency"
                    bid-price: "Overridden Bid Price"
                    offer-currency: "Overridden Offer Currency"
                    offer-price: "Overridden Offer Price"
            """)
        .run(
            context -> {
              var props = context.getBean(FundPriceExportProperties.class);
              var bidOffer = props.getHeaders().getBidOffer();
              assertEquals("Overridden Bid Currency", bidOffer.getBidCurrency());
              assertEquals("Overridden Bid Price", bidOffer.getBidPrice());
              assertEquals("Overridden Offer Currency", bidOffer.getOfferCurrency());
              assertEquals("Overridden Offer Price", bidOffer.getOfferPrice());
            });
  }

  @Test
  void overridingOneLeafDoesNotDisturbUnrelatedDefaults() {
    withYaml(
            """
            fund-price:
              export:
                headers:
                  fund-name: "Overridden Fund Name"
            """)
        .run(
            context -> {
              var props = context.getBean(FundPriceExportProperties.class);
              assertEquals("Overridden Fund Name", props.getHeaders().getFundName());
              // Everything else keeps its default.
              assertEquals("Fund Price Date基金價格日期", props.getHeaders().getFundPriceDate());
              assertEquals("\u57fa\u91d1\u540d\u7a31", props.getHeaders().getChineseFundName());
            });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(FundPriceExportProperties.class)
  static class TestConfig {}

  @Test
  void defaultSheetNamesMatchFundPriceTypes() {
    contextRunner.run(
        context -> {
          var props = context.getBean(FundPriceExportProperties.class);
          assertEquals("BIDOFFER PRICE", props.sheetName("BIDOFFER PRICE"));
          assertEquals("UNIT PRICE", props.sheetName("UNIT PRICE"));
        });
  }

  @Test
  void bracketedYamlKeysKeepTheSpaceInFundPriceType() {
    withYaml(
            """
            fund-price:
              export:
                sheet-names:
                  "[BIDOFFER PRICE]": "Bid Offer"
                  "[UNIT PRICE]": "Unit"
            """)
        .run(
            context -> {
              var props = context.getBean(FundPriceExportProperties.class);
              assertEquals("Bid Offer", props.sheetName("BIDOFFER PRICE"));
              assertEquals("Unit", props.sheetName("UNIT PRICE"));
            });
  }
}
