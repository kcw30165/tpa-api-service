package com.bct.ngtpa.apiservice.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class FundFactSheetPresentationEventLoggerTest {

  private final FundFactSheetPresentationEventLogger events =
      new FundFactSheetPresentationEventLogger();

  private Logger logger;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void attachLogAppender() {
    logger = (Logger) LoggerFactory.getLogger(FundFactSheetPresentationEventLogger.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void detachLogAppender() {
    logger.detachAppender(appender);
    appender.stop();
  }

  @Test
  void missingSeqLogsOneWarnEvent() {
    events.missingSeq();

    assertSingleWarn("event=fund-fact-sheet.missing-seq");
  }

  @Test
  void duplicateSeqLogsOneWarnEvent() {
    events.duplicateSeq();

    assertSingleWarn("event=fund-fact-sheet.duplicate-seq");
  }

  @Test
  void blankEnglishNameLogsOneWarnEvent() {
    events.blankEnglishName();

    assertSingleWarn("event=fund-fact-sheet.blank-english-name");
  }

  @Test
  void malformedFactsheetLogsOneWarnEvent() {
    events.malformedFactsheet();

    assertSingleWarn("event=fund-fact-sheet.malformed-factsheet");
  }

  @Test
  void missingAccountIdentifierLogsOneWarnEvent() {
    events.missingAccountIdentifier();

    assertSingleWarn("event=fund-fact-sheet.missing-account-identifier");
  }

  @Test
  void everyCallLogsExactlyOneEventInCallOrder() {
    events.missingSeq();
    events.duplicateSeq();
    events.blankEnglishName();
    events.malformedFactsheet();
    events.malformedFactsheet();
    events.missingAccountIdentifier();

    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .containsExactly(
            "event=fund-fact-sheet.missing-seq",
            "event=fund-fact-sheet.duplicate-seq",
            "event=fund-fact-sheet.blank-english-name",
            "event=fund-fact-sheet.malformed-factsheet",
            "event=fund-fact-sheet.malformed-factsheet",
            "event=fund-fact-sheet.missing-account-identifier");
  }

  @Test
  void noEventCarriesAPlaceholderOrAValue() {
    events.missingSeq();
    events.duplicateSeq();
    events.blankEnglishName();
    events.malformedFactsheet();
    events.missingAccountIdentifier();

    assertThat(appender.list).hasSize(5);
    for (ILoggingEvent event : appender.list) {
      assertThat(event.getFormattedMessage()).doesNotContain("{}", "http", "=0", "=1");
      Object[] arguments = event.getArgumentArray();
      assertThat(arguments == null || arguments.length == 0).isTrue();
    }
  }

  private void assertSingleWarn(String expectedMessage) {
    assertThat(appender.list).hasSize(1);
    ILoggingEvent event = appender.list.get(0);
    assertThat(event.getLevel()).isEqualTo(Level.WARN);
    assertThat(event.getFormattedMessage()).isEqualTo(expectedMessage);
  }
}
