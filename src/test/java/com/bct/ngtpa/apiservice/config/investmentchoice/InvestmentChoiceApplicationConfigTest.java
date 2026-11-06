package com.bct.ngtpa.apiservice.config.investmentchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bct.ngtpa.apiservice.application.port.in.GetInvestmentChoiceUseCase;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.port.out.InvestmentChoiceDetailsPort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.application.service.account.AccountIdentifierFormatter;
import com.bct.ngtpa.apiservice.application.usecase.investmentchoice.GetInvestmentChoiceService;
import com.bct.ngtpa.apiservice.application.usecase.investmentchoice.InvestmentChoiceSnapshotCacheService;
import com.bct.ngtpa.apiservice.infrastructure.logging.ExecutionLoggingAspect;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

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

  @Test
  void applicationExecutionLoggingCanCreateAProxyForTheUseCaseBean() {
    ObjectMapper objectMapper = new ObjectMapper();
    ExecutionLoggingAspect aspect =
        new ExecutionLoggingAspect(
            new LoggingSanitizer(objectMapper, new MaskingService(new MaskingProperties())),
            objectMapper);

    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class))
        .withUserConfiguration(InvestmentChoiceApplicationConfig.class)
        .withBean(ExecutionLoggingAspect.class, () -> aspect)
        .withBean(ReferenceDatePort.class, () -> mock(ReferenceDatePort.class))
        .withBean(InvestmentChoiceDetailsPort.class, () -> mock(InvestmentChoiceDetailsPort.class))
        .withBean(
            AccountIdentifierFormatter.class, () -> new AccountIdentifierFormatter(11, 4, '0'))
        .withBean(
            InvestmentChoiceSnapshotCacheService.class,
            () -> mock(InvestmentChoiceSnapshotCacheService.class))
        .withBean(FailureEventPort.class, () -> mock(FailureEventPort.class))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(AopUtils.isCglibProxy(context.getBean(GetInvestmentChoiceUseCase.class)))
                  .isTrue();
            });
  }
}
