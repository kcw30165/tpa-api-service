package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetMemberInfoApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetMemberInfoDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.MemberInfoConfigItem;
import com.bct.ngtpa.apiservice.application.dto.FetchMemberInfoCommand;
import com.bct.ngtpa.apiservice.application.dto.MemberInfoResult;
import com.bct.ngtpa.apiservice.application.port.out.ApimMemberInfoPort;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCryptoException;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Adapter for fetching personal/member information from APIM TRPGetMemberInfo endpoint.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApimMemberInfoAdapter implements ApimMemberInfoPort {

    private static final String API_NAME = "/ws/NGTPA/v1/TRPGetMemberInfo";

    private final ApimWebClientFacade apimWebClientFacade;
    private final ApimCertificateService apimCertificateService;
    private final ApimPayloadCryptoService apimPayloadCryptoService;
    private final ApimProperties apimProperties;

    @Override
    public Mono<MemberInfoResult> fetchMemberInfo(FetchMemberInfoCommand command) {
        if (!apimProperties.getEncryption().isEnabled()) {
            var request = apimPayloadCryptoService.encryptRequest(API_NAME, toApimRequest(command), GetMemberInfoApimRequest.class, null);
            return apimWebClientFacade.post(API_NAME, request)
                    .map(body -> apimPayloadCryptoService.decryptResponseEnvelope(API_NAME, body, GetMemberInfoDataItem.class, null))
                    .map(this::toMemberInfoResult)
                    .onErrorMap(ApimCryptoException.class, ex ->
                            new ApimException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.SYSTEM_UNEXPECTED, ex.getMessage(), ex));
        }

        return apimCertificateService.getBctPublicKey()
                .flatMap(publicKey -> {
                    var request = apimPayloadCryptoService.encryptRequest(API_NAME, toApimRequest(command), GetMemberInfoApimRequest.class, publicKey);
                    return apimWebClientFacade.post(API_NAME, request)
                            .map(body -> apimPayloadCryptoService.decryptResponseEnvelope(API_NAME, body, GetMemberInfoDataItem.class, publicKey))
                            .map(this::toMemberInfoResult);
                })
                .onErrorMap(ApimCryptoException.class, ex ->
                        new ApimException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.SYSTEM_UNEXPECTED, ex.getMessage(), ex));
    }

    private GetMemberInfoApimRequest toApimRequest(FetchMemberInfoCommand command) {
        return GetMemberInfoApimRequest.builder()
                .policyNo(command.getPolicyNo())
                .certNo(command.getCertNo())
                .userId(command.getUserId())
                .accountEnv(command.getAccountEnv())
                .build();
    }

    private MemberInfoResult toMemberInfoResult(ApimResponseEnvelope<GetMemberInfoDataItem> response) {
        var payload = response != null ? response.getResponse() : null;
        if (payload == null) {
            throw new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID,
                    "APIM response payload is missing.");
        }
        if (StringUtils.hasText(payload.getErrMessage())) {
            throw new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID,
                    payload.getErrMessage());
        }

        List<GetMemberInfoDataItem> dataItems = payload.getData();
        if (CollectionUtils.isEmpty(dataItems)) {
            return new MemberInfoResult(Map.of());
        }

        GetMemberInfoDataItem first = dataItems.get(0);
        Map<String, Object> result = new LinkedHashMap<>();

        // config -> map of item-id -> config-value
        Map<String, String> configMap = new LinkedHashMap<>();
        if (first.getConfig() != null) {
            for (MemberInfoConfigItem cfg : first.getConfig()) {
                if (cfg != null && cfg.getItemId() != null) {
                    configMap.put(cfg.getItemId(), cfg.getConfigValue());
                }
            }
        }
        result.put("config", configMap);

        // data -> first data object if present
        if (!CollectionUtils.isEmpty(first.getData())) {
            Map<String, Object> dataObj = first.getData().get(0);
            result.put("data", dataObj == null ? Map.of() : dataObj);
        } else {
            result.put("data", Map.of());
        }

        return new MemberInfoResult(result);
    }
}
