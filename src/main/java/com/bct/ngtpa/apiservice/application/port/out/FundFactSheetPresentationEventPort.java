package com.bct.ngtpa.apiservice.application.port.out;

/**
 * Value-free presentation events of the fund fact sheet. No method takes an argument, so a name, a
 * link, a sequence number or an account identifier can never reach a log through this port.
 */
public interface FundFactSheetPresentationEventPort {

  void missingSeq();

  void duplicateSeq();

  void blankEnglishName();

  void malformedFactsheet();

  /** The policy number, the certificate number or the scheme type is null or blank. */
  void missingAccountIdentifier();
}
