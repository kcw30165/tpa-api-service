package com.bct.ngtpa.apiservice.application.dto.investmentchoice;

import java.time.LocalDate;

public record InvestmentChoiceMandate(
    LocalDate effectiveDate, String switchSource, String fundCode, String percentage) {}
