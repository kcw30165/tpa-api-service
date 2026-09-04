package com.bct.ngtpa.apiservice.config.accountgainloss;

import com.bct.ngtpa.apiservice.application.port.in.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.service.accountgainloss.AccountGainLossAssembler;
import com.bct.ngtpa.apiservice.application.usecase.GetAccountGainLossService;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
public class AccountGainLossApplicationConfig {
  @Bean AccountGainLossAssembler accountGainLossAssembler() { return new AccountGainLossAssembler(); }
  @Bean GetAccountGainLossUseCase getAccountGainLossUseCase(
      ReferenceDatePort date, GeneralMetadataResolver metadata,
      AccountGainLossSourcePort source, AccountGainLossAssembler assembler) {
    return new GetAccountGainLossService(date, metadata, source, assembler);
  }
}
