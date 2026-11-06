package com.bct.ngtpa.apiservice.config.investmentchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.GetInvestmentChoiceCommand;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceDetails;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceFund;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceMandate;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSource;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceDetailsRetrievalException;
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
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class InvestmentChoiceApplicationConfigTest {

  private static final String POLICY_NO = "POLICY_MARK";
  private static final String CERT_NO = "3";
  private static final String ACCOUNT_REF = "ACCOUNT_REF_MARKER";
  private static final AuthenticatedSessionId SESSION_ID =
      new AuthenticatedSessionId("sid_session-marker-0123456789ABCDEF");
  private static final String FUND_CODE = "FUND_CODE_MARKER";
  private static final String PERCENTAGE = "PERCENTAGE_MARKER";
  private static final String REQUEST_ID = "request-safe";

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
  void investmentChoiceUseCaseIsNotProxiedOrLoggedByExecutionAspect() {
    Logger logger = (Logger) LoggerFactory.getLogger(ExecutionLoggingAspect.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    AtomicBoolean proxied = new AtomicBoolean(true);

    ObjectMapper objectMapper = new ObjectMapper();
    ExecutionLoggingAspect aspect =
        new ExecutionLoggingAspect(
            new LoggingSanitizer(objectMapper, new MaskingService(new MaskingProperties())),
            objectMapper);

    try {
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class))
          .withUserConfiguration(InvestmentChoiceApplicationConfig.class)
          .withBean(ExecutionLoggingAspect.class, () -> aspect)
          .withBean(ReferenceDatePort.class, () -> mock(ReferenceDatePort.class))
          .withBean(
              InvestmentChoiceDetailsPort.class, () -> mock(InvestmentChoiceDetailsPort.class))
          .withBean(
              AccountIdentifierFormatter.class, () -> new AccountIdentifierFormatter(11, 4, '0'))
          .withBean(
              InvestmentChoiceSnapshotCacheService.class,
              () -> mock(InvestmentChoiceSnapshotCacheService.class))
          .withBean(FailureEventPort.class, () -> mock(FailureEventPort.class))
          .run(
              context -> {
                assertThat(context).hasNotFailed();
                GetInvestmentChoiceUseCase useCase =
                    context.getBean(GetInvestmentChoiceUseCase.class);
                proxied.set(AopUtils.isAopProxy(useCase));
                ReferenceDatePort referenceDates = context.getBean(ReferenceDatePort.class);
                InvestmentChoiceDetailsPort detailsPort =
                    context.getBean(InvestmentChoiceDetailsPort.class);
                InvestmentChoiceSnapshotCacheService snapshotCache =
                    context.getBean(InvestmentChoiceSnapshotCacheService.class);

                when(referenceDates.resolveReferenceDate("SIT"))
                    .thenReturn(Mono.just(LocalDate.of(2026, 10, 15)));
                when(detailsPort.load(any())).thenReturn(Mono.just(details()));
                when(snapshotCache.write(any(), any(), any())).thenReturn(Mono.empty());

                StepVerifier.create(useCase.get(command(POLICY_NO)))
                    .expectNextCount(1)
                    .verifyComplete();

                when(detailsPort.load(any()))
                    .thenReturn(
                        Mono.error(InvestmentChoiceDetailsRetrievalException.systemFailure()));
                StepVerifier.create(useCase.get(command(POLICY_NO)))
                    .expectError(InvestmentChoiceDetailsRetrievalException.class)
                    .verify();

                when(detailsPort.load(any())).thenReturn(Mono.just(details()));
                StepVerifier.create(useCase.get(command("POLICY_MARKER_TOO_LONG")))
                    .expectError(InvestmentChoiceDetailsRetrievalException.class)
                    .verify();
              });

      List<String> messages =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
      String allMessages = messages.stream().collect(Collectors.joining("\n"));
      SoftAssertions softly = new SoftAssertions();
      softly.assertThat(proxied.get()).as("the use-case bean is not an AOP proxy").isFalse();
      softly
          .assertThat(messages)
          .noneMatch(message -> message.contains("\"event\":\"method.execution."));
      softly
          .assertThat(appender.list)
          .noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.ERROR));
      softly
          .assertThat(allMessages)
          .doesNotContain(
              POLICY_NO,
              ACCOUNT_REF,
              SESSION_ID.value(),
              FUND_CODE,
              PERCENTAGE,
              "POLICY_MARKER_TOO_LONG",
              "\"certNo\":\"" + CERT_NO + "\"");
      softly.assertAll();
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  private static GetInvestmentChoiceCommand command(String policyNo) {
    return new GetInvestmentChoiceCommand(
        ActorUserRole.ROLE_USER_MBR, "SIT", policyNo, CERT_NO, ACCOUNT_REF, SESSION_ID, REQUEST_ID);
  }

  private static InvestmentChoiceDetails details() {
    return new InvestmentChoiceDetails(
        25,
        List.of(new InvestmentChoiceSource("BO", 1, true)),
        List.of(new InvestmentChoiceFund(FUND_CODE, "Fund", "", "1", true)),
        List.of(new InvestmentChoiceMandate(LocalDate.of(2025, 1, 1), "BO", FUND_CODE, PERCENTAGE)),
        List.of());
  }
}
