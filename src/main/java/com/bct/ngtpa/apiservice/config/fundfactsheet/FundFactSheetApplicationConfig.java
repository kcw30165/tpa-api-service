package com.bct.ngtpa.apiservice.config.fundfactsheet;

import com.bct.ngtpa.apiservice.application.port.in.GetFundFactSheetUseCase;
import com.bct.ngtpa.apiservice.application.port.out.FundFactSheetPresentationEventPort;
import com.bct.ngtpa.apiservice.application.port.out.FundFactSheetSourcePort;
import com.bct.ngtpa.apiservice.application.service.presentation.UrlValidator;
import com.bct.ngtpa.apiservice.application.usecase.fundfactsheet.FundFactSheetNormalizer;
import com.bct.ngtpa.apiservice.application.usecase.fundfactsheet.GetFundFactSheetService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the fund fact sheet use case. The application classes carry no Spring annotation. The
 * {@link UrlValidator} is not a bean: it is constructed here, inline, with the malformed-link event
 * of this feature. {@link FundFactSheetSourcePort} needs no explicit bean, because the APIM adapter
 * is a component that implements it.
 */
@Configuration(proxyBeanMethods = false)
public class FundFactSheetApplicationConfig {

  @Bean
  FundFactSheetNormalizer fundFactSheetNormalizer(FundFactSheetPresentationEventPort events) {
    return new FundFactSheetNormalizer(new UrlValidator(events::malformedFactsheet), events);
  }

  @Bean
  GetFundFactSheetUseCase getFundFactSheetUseCase(
      FundFactSheetSourcePort source,
      FundFactSheetNormalizer normalizer,
      FundFactSheetPresentationEventPort events) {
    return new GetFundFactSheetService(source, normalizer, events);
  }
}
