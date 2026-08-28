package com.bct.ngtpa.apiservice.application.dto;
import java.util.List;
public record InvestmentPortfolio(List<FundLevelDisplayAccount> fundLevelView,List<SubDisplayLevelDisplayAccount> subDisplayAccountLevelView){public InvestmentPortfolio{fundLevelView=fundLevelView==null?List.of():List.copyOf(fundLevelView);subDisplayAccountLevelView=subDisplayAccountLevelView==null?List.of():List.copyOf(subDisplayAccountLevelView);}}
