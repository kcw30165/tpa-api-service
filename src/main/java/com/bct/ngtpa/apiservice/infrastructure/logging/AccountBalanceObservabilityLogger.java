package com.bct.ngtpa.apiservice.infrastructure.logging;

import com.bct.ngtpa.apiservice.application.port.out.AccountBalanceObservabilityPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component @Slf4j @RequiredArgsConstructor
public final class AccountBalanceObservabilityLogger implements AccountBalanceObservabilityPort {
  private final ObjectMapper objectMapper;
  public void completed(String requestId, Phase phase, long durationMs, int validatedCount, boolean fundLevelViewEnabled, boolean subDisplayLevelViewEnabled) {
    Map<String,Object> e=base("account-balance.completed",requestId,phase,durationMs);
    e.put("validatedCount",Math.max(0,validatedCount)); e.put("fundLevelViewEnabled",fundLevelViewEnabled); e.put("subDisplayLevelViewEnabled",subDisplayLevelViewEnabled); log.info("{}",json(e));
  }
  public void failed(String requestId, Phase phase, long durationMs, FailureCategory failureCategory) {
    Map<String,Object> e=base("account-balance.failed",requestId,phase,durationMs); e.put("failureCategory",failureCategory.name()); log.warn("{}",json(e));
  }
  private Map<String,Object> base(String event,String requestId,Phase phase,long durationMs){ Map<String,Object> e=new LinkedHashMap<>(); e.put("event",event); if(requestId!=null&&!requestId.isBlank())e.put("requestId",requestId); e.put("operation","retrieve"); e.put("phase",phase.name()); e.put("durationMs",Math.max(0,durationMs)); return e; }
  private String json(Object value){ try{return objectMapper.writeValueAsString(value);}catch(JsonProcessingException ex){return "{\"event\":\"log-serialization-failed\"}";} }
}
