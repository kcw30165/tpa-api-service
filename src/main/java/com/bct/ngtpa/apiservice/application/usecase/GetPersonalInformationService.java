package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.FetchMemberInfoCommand;
import com.bct.ngtpa.apiservice.application.dto.GetPersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.dto.MemberInfoConfigItem;
import com.bct.ngtpa.apiservice.application.dto.MemberInfoConfigItemType;
import com.bct.ngtpa.apiservice.application.dto.MemberInfoResult;
import com.bct.ngtpa.apiservice.application.dto.PersonalInformationResult;
import com.bct.ngtpa.apiservice.application.port.in.GetPersonalInformationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimMemberInfoPort;
import com.bct.ngtpa.apiservice.application.port.out.PortalAccessContextPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class GetPersonalInformationService implements GetPersonalInformationUseCase {

    private final ApimMemberInfoPort apimMemberInfoPort;
    private final PortalAccessContextPort portalAccessContextPort;

    @Override
    public Mono<PersonalInformationResult> execute(GetPersonalInformationCommand command) {
        String accountRef = command.accountRef();
        return portalAccessContextPort.resolvePortalAccessContext(accountRef)
                .flatMap(ctx -> {
                    var apimCmd = new FetchMemberInfoCommand(
                            ctx.account().accountEnv(),
                            ctx.account().policyNo(),
                            ctx.account().certNo(),
                            ctx.actor().actorUserId()
                    );
                    return apimMemberInfoPort.fetchMemberInfo(apimCmd)
                            .map(this::toResult);
                });
    }

    private PersonalInformationResult toResult(MemberInfoResult memberInfoResult) {
        Map<String, Object> payload = memberInfoResult != null && memberInfoResult.getPayload() != null
                ? normalizePayload(memberInfoResult.getPayload())
                : Map.of();
        return new PersonalInformationResult(extractData(payload), extractConfig(payload), extractConfigItems(payload));
    }

    /**
     * Supports both the existing flattened payload shape and the raw APIM envelope shape:
     * response.data[0].config / response.data[0].data.
     */
    private Map<String, Object> normalizePayload(Map<String, Object> payload) {
        Object responseObj = payload.get("response");
        if (responseObj instanceof Map<?, ?> responseMap) {
            Map<String, Object> firstResponseData = firstMap(responseMap.get("data"));
            if (!firstResponseData.isEmpty()) {
                return firstResponseData;
            }
        }

        Map<String, Object> firstData = firstMap(payload.get("data"));
        if (!firstData.isEmpty() && (firstData.containsKey("config") || firstData.containsKey("data"))) {
            return firstData;
        }

        return payload;
    }

    private Map<String, Object> extractData(Map<String, Object> payload) {
        Map<String, Object> apimData = new LinkedHashMap<>();
        Object dataObj = payload.get("data");
        if (dataObj instanceof Map<?, ?> dataMap) {
            putAllStringKeys(apimData, dataMap);
        } else if (dataObj instanceof List<?> dataList) {
            for (Object itemObj : dataList) {
                if (itemObj instanceof Map<?, ?> itemMap) {
                    putAllStringKeys(apimData, itemMap);
                }
            }
        }
        return apimData;
    }

    private Map<String, String> extractConfig(Map<String, Object> payload) {
        Map<String, String> apimConfig = new LinkedHashMap<>();
        Object cfgObj = payload.get("config");
        if (cfgObj instanceof Map<?, ?> cfgMap) {
            for (Map.Entry<?, ?> e : cfgMap.entrySet()) {
                apimConfig.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            }
        }
        return apimConfig;
    }

    private Map<String, MemberInfoConfigItem> extractConfigItems(Map<String, Object> payload) {
        Map<String, MemberInfoConfigItem> configItems = new LinkedHashMap<>();
        Object cfgObj = payload.get("config");
        if (cfgObj instanceof List<?> cfgList) {
            for (Object itemObj : cfgList) {
                if (itemObj instanceof Map<?, ?> itemMap) {
                    MemberInfoConfigItem item = toConfigItem(itemMap);
                    if (item.itemId() != null) {
                        configItems.put(item.itemId(), item);
                    }
                }
            }
        }
        return configItems;
    }

    private MemberInfoConfigItem toConfigItem(Map<?, ?> itemMap) {
        String itemId = stringValue(itemMap.get("item-id"));
        String itemName = stringValue(itemMap.get("item-name"));
        String itemType = stringValue(itemMap.get("item-type"));
        String function = stringValue(itemMap.get("function"));
        String schType = stringValue(itemMap.get("sch-type"));
        String configValue = stringValue(itemMap.get("config-value"));
        return new MemberInfoConfigItem(
                itemId,
                itemName,
                MemberInfoConfigItemType.fromCode(itemType),
                function,
                schType,
                configValue);
    }

    private Map<String, Object> firstMap(Object value) {
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> result = new LinkedHashMap<>();
                    putAllStringKeys(result, map);
                    return result;
                }
            }
        }
        return Map.of();
    }

    private void putAllStringKeys(Map<String, Object> target, Map<?, ?> source) {
        for (Map.Entry<?, ?> e : source.entrySet()) {
            target.put(String.valueOf(e.getKey()), e.getValue());
        }
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String trimmed = String.valueOf(value).trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
