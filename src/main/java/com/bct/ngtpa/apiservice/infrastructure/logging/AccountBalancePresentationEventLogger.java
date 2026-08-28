package com.bct.ngtpa.apiservice.infrastructure.logging;

import com.bct.ngtpa.apiservice.application.port.out.AccountBalancePresentationEventPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public final class AccountBalancePresentationEventLogger
    implements AccountBalancePresentationEventPort {
  @Override
  public void malformedFactsheet() {
    log.warn("event=account-balance.presentation.factsheet.omitted reason=malformed");
  }
}
