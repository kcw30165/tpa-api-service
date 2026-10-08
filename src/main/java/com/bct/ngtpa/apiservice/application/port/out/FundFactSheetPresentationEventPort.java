package com.bct.ngtpa.apiservice.application.port.out;

/**
 * Value-free presentation events of the fund fact sheet. No method takes an argument, so a name, a
 * link or a sequence number can never reach a log through this port.
 */
public interface FundFactSheetPresentationEventPort {

  void missingSeq();

  void duplicateSeq();

  void blankEnglishName();

  void malformedFactsheet();
}
