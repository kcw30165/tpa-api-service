package com.bct.ngtpa.apiservice.config.fundprice;

import com.bct.ngtpa.apiservice.adapter.in.web.fundprice.config.FundPriceExportProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.fundprice.support.FundPriceWorkbookExporter;
import com.bct.ngtpa.apiservice.application.port.in.GeneralMetadataResolver;
import com.bct.ngtpa.apiservice.application.port.out.FundPriceSourcePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.application.usecase.fundprice.FundPriceExportUseCase;
import com.bct.ngtpa.apiservice.application.usecase.fundprice.FundPriceQueryUseCase;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.LoadAndTouchAuthenticatedSessionUseCase;
import java.time.format.DateTimeFormatter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires {@link FundPriceQueryUseCase} and {@link FundPriceExportUseCase}, mirroring {@code
 * UseCaseConfig}'s exact established pattern for every other use case in this codebase: plain
 * {@code @Configuration}, {@code @Bean} methods taking each use case's real constructor
 * dependencies, returning a direct {@code new} instance. Neither use case class carries a Spring
 * stereotype annotation itself, keeping the application layer framework-free.
 *
 * <p>Closes a real, confirmed Spring startup failure (real {@code spring-boot:run} smoke test,
 * not caught by {@code mvn test} alone, since every unit test for these classes constructs them
 * manually): {@link FundPriceQueryUseCase}, {@link FundPriceExportUseCase}, and {@link
 * FundPriceWorkbookExporter} previously had NO {@code @Bean} definitions anywhere in the
 * codebase, unlike their sibling {@link
 * com.bct.ngtpa.apiservice.application.usecase.fundprice.FundPriceModeUseCase}, which was already
 * correctly wired via {@code FundPriceModeApplicationConfig}.
 *
 * <p>Every dependency type below already has a real, working bean elsewhere in the application
 * (confirmed): {@link FundPriceSourcePort} ({@code ApimFundPriceSourceAdapter}), {@link
 * ReferenceDatePort} ({@code ReferenceDateAdapter}), {@link GeneralMetadataResolver} ({@code
 * GeneralMetadataResolverService}), {@link LoadAndTouchAuthenticatedSessionUseCase} (already used
 * elsewhere in this feature), {@link FundPriceExportProperties} (auto-registered via {@code
 * NgtpaApiServerApplication}'s {@code @ConfigurationPropertiesScan}, no explicit {@code
 * @EnableConfigurationProperties} needed), and the {@code fundPriceFullDateFormatter} {@link
 * DateTimeFormatter} bean ({@code FundPriceDateFormatValidationConfig}).
 */
@Configuration
public class FundPriceQueryExportUseCaseConfig {

  @Bean
  public FundPriceQueryUseCase fundPriceQueryUseCase(
      LoadAndTouchAuthenticatedSessionUseCase lifecycle,
      FundPriceSourcePort sourcePort,
      ReferenceDatePort referenceDatePort) {
    return new FundPriceQueryUseCase(lifecycle, sourcePort, referenceDatePort);
  }

  @Bean
  public FundPriceExportUseCase fundPriceExportUseCase(
      LoadAndTouchAuthenticatedSessionUseCase lifecycle,
      FundPriceSourcePort sourcePort,
      ReferenceDatePort referenceDatePort,
      GeneralMetadataResolver generalMetadataResolver) {
    return new FundPriceExportUseCase(
        lifecycle, sourcePort, referenceDatePort, generalMetadataResolver);
  }

  @Bean
  public FundPriceWorkbookExporter fundPriceWorkbookExporter(
      FundPriceExportProperties properties,
      DateTimeFormatter fundPriceFullDateFormatter,
      com.bct.ngtpa.apiservice.adapter.out.apim.fundprice.FundPriceDateFormatProperties
          dateFormatProperties) {
    // Month formatter is passed directly rather than exposed as a second DateTimeFormatter
    // bean, which would make by-type injection of fundPriceFullDateFormatter ambiguous.
    dateFormatProperties.validate();
    return new FundPriceWorkbookExporter(
        properties, fundPriceFullDateFormatter, dateFormatProperties.monthFormatter());
  }
}
