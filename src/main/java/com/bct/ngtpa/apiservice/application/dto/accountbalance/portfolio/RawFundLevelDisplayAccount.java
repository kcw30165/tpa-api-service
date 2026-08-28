package com.bct.ngtpa.apiservice.application.dto.accountbalance.portfolio;
import java.util.List;
public record RawFundLevelDisplayAccount(String displayAccountId,Integer displaySequence,List<RawFundLevelFund> funds){ public RawFundLevelDisplayAccount{ funds=funds==null?List.of():List.copyOf(funds); } }
