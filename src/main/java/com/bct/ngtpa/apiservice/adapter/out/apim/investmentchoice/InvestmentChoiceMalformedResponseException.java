package com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice;

final class InvestmentChoiceMalformedResponseException extends RuntimeException {

  InvestmentChoiceMalformedResponseException() {
    super("Malformed investment-choice APIM response");
  }
}
