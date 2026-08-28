package com.bct.ngtpa.apiservice.application.dto.accountbalance.portfolio;
import java.math.BigDecimal; import java.util.List;
public record RawFundLevelFund(String fundCode,Integer fundSequence,BigDecimal unitBalance,BigDecimal accountBalance,List<RawFundLevelSubDisplayAccount> subDisplayAccounts){ public RawFundLevelFund{ subDisplayAccounts=subDisplayAccounts==null?List.of():List.copyOf(subDisplayAccounts); } }
