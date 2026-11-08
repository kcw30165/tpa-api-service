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
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceInstructionRequest;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceMandate;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoicePending;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoicePendingDetail;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSnapshot;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSource;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSubmission;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSubmitOutcome;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.SubmitInvestmentChoiceCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceDetailsRetrievalException;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceSubmissionRejectedException;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceSubmissionUnavailableException;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceSubmissionUnknownOutcomeException;
import com.bct.ngtpa.apiservice.application.port.in.GetInvestmentChoiceUseCase;
import com.bct.ngtpa.apiservice.application.port.in.SubmitInvestmentChoiceUseCase;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.port.out.InvestmentChoiceDetailsPort;
import com.bct.ngtpa.apiservice.application.port.out.InvestmentChoiceInstructionPort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.application.service.account.AccountIdentifierFormatter;
import com.bct.ngtpa.apiservice.application.usecase.investmentchoice.GetInvestmentChoiceService;
import com.bct.ngtpa.apiservice.application.usecase.investmentchoice.InvestmentChoiceSnapshotCacheService;
import com.bct.ngtpa.apiservice.application.usecase.investmentchoice.SubmitInvestmentChoiceService;
import com.bct.ngtpa.apiservice.infrastructure.logging.ExecutionLoggingAspect;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
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
  private static final String SUBMIT_CERT_NO = "SUBMIT_CERT_MARKER";
  private static final String ACCOUNT_REF = "ACCOUNT_REF_MARKER";
  private static final String ACTOR_USER_ID = "ACTOR_USER_MARKER";
  private static final AuthenticatedSessionId SESSION_ID =
      new AuthenticatedSessionId("sid_session-marker-0123456789ABCDEF");
  private static final String FUND_CODE = "FBAL";
  private static final String OTHER_FUND_CODE = "FHKM";
  private static final String PERCENTAGE = "60";
  private static final String REQUEST_ID = "request-safe";
  private static final InvestmentChoiceInstructionRequest VALID_REQUEST =
      new InvestmentChoiceInstructionRequest(
          List.of("BO", "EE"),
          List.of(
              new InvestmentChoiceInstructionRequest.Allocation(FUND_CODE, 60),
              new InvestmentChoiceInstructionRequest.Allocation(OTHER_FUND_CODE, 40)));

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

    assertThat(useCase).isNotNull().isInstanceOf(GetInvestmentChoiceService.class);
  }

  @Test
  void investmentChoiceUseCaseIsNotProxiedOrLoggedByExecutionAspect() {
    Logger logger = (Logger) LoggerFactory.getLogger(ExecutionLoggingAspect.class);
    Logger submitLogger = (Logger) LoggerFactory.getLogger(SubmitInvestmentChoiceService.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    submitLogger.addAppender(appender);
    AtomicBoolean getProxied = new AtomicBoolean(true);
    AtomicBoolean submitProxied = new AtomicBoolean(true);

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
          .withBean(
              InvestmentChoiceInstructionPort.class,
              () -> mock(InvestmentChoiceInstructionPort.class))
          .withBean(
              Clock.class, () -> Clock.fixed(Instant.parse("2026-11-05T16:30:00Z"), ZoneOffset.UTC))
          .withBean(FailureEventPort.class, () -> mock(FailureEventPort.class))
          .run(
              context -> {
                assertThat(context).hasNotFailed();
                GetInvestmentChoiceUseCase getUseCase =
                    context.getBean(GetInvestmentChoiceUseCase.class);
                SubmitInvestmentChoiceUseCase submitUseCase =
                    context.getBean(SubmitInvestmentChoiceUseCase.class);
                getProxied.set(AopUtils.isAopProxy(getUseCase));
                submitProxied.set(AopUtils.isAopProxy(submitUseCase));
                ReferenceDatePort referenceDates = context.getBean(ReferenceDatePort.class);
                InvestmentChoiceDetailsPort detailsPort =
                    context.getBean(InvestmentChoiceDetailsPort.class);
                InvestmentChoiceSnapshotCacheService snapshotCache =
                    context.getBean(InvestmentChoiceSnapshotCacheService.class);
                InvestmentChoiceInstructionPort instructionPort =
                    context.getBean(InvestmentChoiceInstructionPort.class);

                when(referenceDates.resolveReferenceDate("SIT"))
                    .thenReturn(Mono.just(LocalDate.of(2026, 10, 15)));
                when(detailsPort.load(any())).thenReturn(Mono.just(details()));
                when(snapshotCache.write(any(), any(), any())).thenReturn(Mono.empty());

                StepVerifier.create(getUseCase.get(command(POLICY_NO)))
                    .expectNextCount(1)
                    .verifyComplete();

                when(detailsPort.load(any()))
                    .thenReturn(
                        Mono.error(InvestmentChoiceDetailsRetrievalException.systemFailure()));
                StepVerifier.create(getUseCase.get(command(POLICY_NO)))
                    .expectError(InvestmentChoiceDetailsRetrievalException.class)
                    .verify();

                when(detailsPort.load(any())).thenReturn(Mono.just(details()));
                StepVerifier.create(getUseCase.get(command("POLICY_MARKER_TOO_LONG")))
                    .expectError(InvestmentChoiceDetailsRetrievalException.class)
                    .verify();

                when(snapshotCache.read(any(), any(), any(), any()))
                    .thenReturn(Mono.just(java.util.Optional.of(snapshot())));
                when(instructionPort.submit(any()))
                    .thenReturn(
                        Mono.just(
                            new InvestmentChoiceSubmission(
                                "submission-reference-sensitive",
                                LocalDate.of(2026, 11, 6),
                                LocalTime.of(9, 15))));
                StepVerifier.create(submitUseCase.submit(submitCommand(VALID_REQUEST)))
                    .expectNextCount(1)
                    .verifyComplete();

                when(instructionPort.submit(any()))
                    .thenReturn(Mono.error(new InvestmentChoiceSubmissionRejectedException()));
                StepVerifier.create(submitUseCase.submit(submitCommand(VALID_REQUEST)))
                    .expectError(InvestmentChoiceSubmissionUnavailableException.class)
                    .verify();

                when(instructionPort.submit(any()))
                    .thenReturn(
                        Mono.error(new InvestmentChoiceSubmissionUnknownOutcomeException()));
                when(detailsPort.load(any())).thenReturn(Mono.just(detailsWithPending()));
                StepVerifier.create(submitUseCase.submit(submitCommand(VALID_REQUEST)))
                    .expectNextCount(1)
                    .verifyComplete();

                when(detailsPort.load(any())).thenReturn(Mono.just(details()));
                StepVerifier.create(submitUseCase.submit(submitCommand(VALID_REQUEST)))
                    .expectError(InvestmentChoiceSubmissionUnavailableException.class)
                    .verify();

                when(snapshotCache.read(any(), any(), any(), any()))
                    .thenReturn(Mono.just(java.util.Optional.empty()));
                StepVerifier.create(submitUseCase.submit(submitCommand(VALID_REQUEST)))
                    .expectError(InvestmentChoiceSubmissionUnavailableException.class)
                    .verify();

                when(snapshotCache.read(any(), any(), any(), any()))
                    .thenReturn(Mono.just(java.util.Optional.of(unavailableSnapshot())));
                StepVerifier.create(submitUseCase.submit(submitCommand(VALID_REQUEST)))
                    .assertNext(
                        outcome ->
                            assertThat(outcome)
                                .isInstanceOf(InvestmentChoiceSubmitOutcome.Invalid.class))
                    .verifyComplete();
              });

      List<String> messages =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
      String allMessages = messages.stream().collect(Collectors.joining("\n"));
      SoftAssertions softly = new SoftAssertions();
      softly.assertThat(getProxied.get()).as("the GET use-case bean is not proxied").isFalse();
      softly
          .assertThat(submitProxied.get())
          .as("the submit use-case bean is not proxied")
          .isFalse();
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
              ACTOR_USER_ID,
              SESSION_ID.value(),
              FUND_CODE,
              OTHER_FUND_CODE,
              PERCENTAGE,
              "40",
              "submission-reference-sensitive",
              "pending-reference-sensitive",
              "POLICY_MARKER_TOO_LONG",
              SUBMIT_CERT_NO,
              "\"certNo\":\"" + CERT_NO + "\"");
      softly.assertAll();
    } finally {
      logger.detachAppender(appender);
      submitLogger.detachAppender(appender);
      appender.stop();
    }
  }

  private static SubmitInvestmentChoiceCommand submitCommand(
      InvestmentChoiceInstructionRequest request) {
    return new SubmitInvestmentChoiceCommand(
        ACTOR_USER_ID,
        ActorUserRole.ROLE_USER_MBR,
        "SIT",
        POLICY_NO,
        SUBMIT_CERT_NO,
        ACCOUNT_REF,
        SESSION_ID,
        REQUEST_ID,
        request);
  }

  private static InvestmentChoiceSnapshot snapshot() {
    return new InvestmentChoiceSnapshot(
        ACCOUNT_REF,
        POLICY_NO,
        20,
        List.of(
            new InvestmentChoiceSnapshot.SwitchSource("BO", true),
            new InvestmentChoiceSnapshot.SwitchSource("EE", true)),
        List.of(
            new InvestmentChoiceSnapshot.Fund(FUND_CODE, true),
            new InvestmentChoiceSnapshot.Fund(OTHER_FUND_CODE, true)),
        List.of());
  }

  private static InvestmentChoiceSnapshot unavailableSnapshot() {
    return new InvestmentChoiceSnapshot(
        ACCOUNT_REF, POLICY_NO, 20, List.of(), List.of(), List.of());
  }

  private static InvestmentChoiceDetails detailsWithPending() {
    var pending =
        new InvestmentChoicePending(
            "pending-reference-sensitive",
            LocalDate.of(2026, 11, 6),
            LocalTime.of(9, 15),
            List.of(
                new InvestmentChoicePendingDetail("BO", FUND_CODE, 60),
                new InvestmentChoicePendingDetail("BO", OTHER_FUND_CODE, 40),
                new InvestmentChoicePendingDetail("EE", FUND_CODE, 60),
                new InvestmentChoicePendingDetail("EE", OTHER_FUND_CODE, 40)));
    return new InvestmentChoiceDetails(20, List.of(), List.of(), List.of(), List.of(pending));
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
