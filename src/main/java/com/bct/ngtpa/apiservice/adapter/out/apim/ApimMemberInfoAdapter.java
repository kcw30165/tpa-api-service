package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetMemberInfoApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetMemberInfoDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.MemberInfoConfigItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.FetchMemberInfoCommand;
import com.bct.ngtpa.apiservice.application.dto.MemberInfoResult;
import com.bct.ngtpa.apiservice.application.port.out.ApimMemberInfoPort;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Mono;

/** Executor-backed adapter for authenticated TRPGetMemberInfo retrieval. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApimMemberInfoAdapter implements ApimMemberInfoPort {
  private static final String API_NAME = "/ws/NGTPA/v1/TRPGetMemberInfo";

  private final ApimExchangeExecutor executor;

  @Override
  public Mono<MemberInfoResult> fetchMemberInfo(FetchMemberInfoCommand command) {
    var request =
        new ApimExchangeRequest<>(
            API_NAME,
            toApimRequest(command),
            GetMemberInfoApimRequest.class,
            GetMemberInfoDataItem.class,
            ApimDatabaseScope.ACCOUNT_ENV,
            new ApimCredentialResolutionContext(
                command.getActorUserRole(), command.getAccountEnv()),
            ApimRetryEligibility.SAFE_BUSINESS_CALL);
    return executor.postEnvelope(request).map(this::toMemberInfoResult);
  }

  private GetMemberInfoApimRequest toApimRequest(FetchMemberInfoCommand command) {
    return GetMemberInfoApimRequest.builder()
        .policyNo(command.getPolicyNo())
        .certNo(command.getCertNo())
        .loginUserId(command.getUserId())
        .loginUserRole(command.getActorUserRole().toApimValue())
        .build();
  }

  private MemberInfoResult toMemberInfoResult(
      ApimResponseEnvelope<GetMemberInfoDataItem> response) {
    var dataItems = ApimResponseValidator.requireSuccessData(response);
    if (CollectionUtils.isEmpty(dataItems)) {
      return new MemberInfoResult(Map.of());
    }
    GetMemberInfoDataItem first = dataItems.get(0);
    Map<String, Object> result = new LinkedHashMap<>();
    Map<String, String> configMap = new LinkedHashMap<>();
    if (first != null && first.getConfig() != null) {
      for (MemberInfoConfigItem cfg : first.getConfig()) {
        if (cfg != null && cfg.getItemId() != null) {
          configMap.put(cfg.getItemId(), cfg.getConfigValue());
        }
      }
    }
    result.put("config", configMap);
    if (first != null && !CollectionUtils.isEmpty(first.getData())) {
      Map<String, Object> dataObj = first.getData().get(0);
      result.put("data", dataObj == null ? Map.of() : dataObj);
    } else {
      result.put("data", Map.of());
    }
    return new MemberInfoResult(result);
  }
}
