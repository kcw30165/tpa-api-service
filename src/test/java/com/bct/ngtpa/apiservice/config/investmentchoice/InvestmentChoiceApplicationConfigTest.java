package com.bct.ngtpa.apiservice.config.investmentchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.port.out.InvestmentChoiceDetailsPort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.application.service.account.AccountIdentifierFormatter;
import com.bct.ngtpa.apiservice.application.usecase.investmentchoice.GetInvestmentChoiceService;
import com.bct.ngtpa.apiservice.application.usecase.investmentchoice.InvestmentChoiceSnapshotCacheService;
import org.junit.jupiter.api.Test;

class InvestmentChoiceApplicationConfigTest {

  @Test
  void wiresTheUseCaseFromItsCollaborators() {
    var useCase =
        new InvestmentChoiceApplicationConfig()
            .getInvestmentChoiceUseCase(
                mock(ReferenceDatePort.class),
                mock(InvestmentChoiceDetailsPort.class),
                new AccountIdentifierFormatter(11, 4, '0'),
                mock(InvestmentChoiceSnapshotCacheService.class),
                mock(FailureEventPort.class));

    assertThat(useCase).isNotNull();
    assertThat(useCase).isInstanceOf(GetInvestmentChoiceService.class);
  }
}
