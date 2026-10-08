package com.bct.ngtpa.apiservice.infrastructure.logging;

import com.bct.ngtpa.apiservice.application.port.out.FundFactSheetPresentationEventPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Writes the value-free presentation events of the fund fact sheet. */
@Component
@Slf4j
public final class FundFactSheetPresentationEventLogger
    implements FundFactSheetPresentationEventPort {

  @Override
  public void missingSeq() {
    log.warn("event=fund-fact-sheet.missing-seq");
  }

  @Override
  public void duplicateSeq() {
    log.warn("event=fund-fact-sheet.duplicate-seq");
  }

  @Override
  public void blankEnglishName() {
    log.warn("event=fund-fact-sheet.blank-english-name");
  }

  @Override
  public void malformedFactsheet() {
    log.warn("event=fund-fact-sheet.malformed-factsheet");
  }

  @Override
  public void missingAccountIdentifier() {
    log.warn("event=fund-fact-sheet.missing-account-identifier");
  }
}
