package com.bct.ngtpa.apiservice.config.investmentchoice;

import com.bct.ngtpa.apiservice.application.port.in.GetInvestmentChoiceUseCase;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.port.out.InvestmentChoiceDetailsPort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.application.service.account.AccountIdentifierFormatter;
import com.bct.ngtpa.apiservice.application.usecase.investmentchoice.GetInvestmentChoiceService;
import com.bct.ngtpa.apiservice.application.usecase.investmentchoice.InvestmentChoiceSnapshotCacheService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class InvestmentChoiceApplicationConfig {

  @Bean
  GetInvestmentChoiceUseCase getInvestmentChoiceUseCase(
      ReferenceDatePort referenceDatePort,
      InvestmentChoiceDetailsPort detailsPort,
      AccountIdentifierFormatter accountIdentifierFormatter,
      InvestmentChoiceSnapshotCacheService snapshotCache,
      FailureEventPort failures) {
    return new GetInvestmentChoiceService(
        referenceDatePort, detailsPort, accountIdentifierFormatter, snapshotCache, failures);
  }
}
