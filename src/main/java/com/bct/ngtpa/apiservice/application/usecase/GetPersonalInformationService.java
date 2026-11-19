package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.FetchMemberInfoCommand;
import com.bct.ngtpa.apiservice.application.dto.GetPersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.dto.MemberInfoConfigItem;
import com.bct.ngtpa.apiservice.application.dto.MemberInfoConfigItemType;
import com.bct.ngtpa.apiservice.application.dto.MemberInfoResult;
import com.bct.ngtpa.apiservice.application.dto.PersonalInformationResult;
import com.bct.ngtpa.apiservice.application.port.in.GetPersonalInformationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimMemberInfoPort;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class GetPersonalInformationService implements GetPersonalInformationUseCase {
  private static final String KEY_CONFIG = "config";
  private static final String KEY_CONFIG_ITEM_TYPES = "configItemTypes";

  private final ApimMemberInfoPort apimMemberInfoPort;

  @Override
  public Mono<PersonalInformationResult> execute(GetPersonalInformationCommand command) {
    return apimMemberInfoPort
        .fetchMemberInfo(
            new FetchMemberInfoCommand(
                command.accountEnv(),
                command.policyNo(),
                command.certNo(),
                command.actorUserId(),
                command.actorUserRole()))
        .map(this::toResult);
  }

  private PersonalInformationResult toResult(MemberInfoResult memberInfoResult) {
    Map<String, Object> payload =
        memberInfoResult != null && memberInfoResult.getPayload() != null
            ? normalizePayload(memberInfoResult.getPayload())
            : Map.of();
    return new PersonalInformationResult(
        extractData(payload),
        extractConfig(payload),
        extractConfigItems(payload),
        extractConfigItemTypes(payload));
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
    if (!firstData.isEmpty()
        && (firstData.containsKey(KEY_CONFIG) || firstData.containsKey("data"))) {
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
    Object cfgObj = payload.get(KEY_CONFIG);
    if (cfgObj instanceof Map<?, ?> cfgMap) {
      Object itemTypesObj = payload.get(KEY_CONFIG_ITEM_TYPES);
      Map<?, ?> itemTypes = itemTypesObj instanceof Map<?, ?> map ? map : null;
      return extractFlattenedConfig(cfgMap, itemTypes);
    }
    if (cfgObj instanceof List<?> cfgList) {
      return extractRawListConfig(cfgList);
    }
    return new LinkedHashMap<>();
  }

  private Map<String, String> extractFlattenedConfig(Map<?, ?> cfgMap, Map<?, ?> itemTypes) {
    Map<String, String> apimConfig = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : cfgMap.entrySet()) {
      String itemId = String.valueOf(entry.getKey());
      if (itemTypes != null && !isEligibleItemType(itemTypes.get(itemId))) {
        continue;
      }
      apimConfig.put(itemId, entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
    }
    return apimConfig;
  }

  private Map<String, String> extractRawListConfig(List<?> cfgList) {
    Map<String, String> apimConfig = new LinkedHashMap<>();
    for (Object itemObj : cfgList) {
      if (itemObj instanceof Map<?, ?> itemMap && isEligibleConfigItem(itemMap)) {
        MemberInfoConfigItem item = toConfigItem(itemMap);
        if (item.itemId() != null) {
          apimConfig.put(item.itemId(), item.configValue() == null ? "" : item.configValue());
        }
      }
    }
    return apimConfig;
  }

  private Map<String, MemberInfoConfigItem> extractConfigItems(Map<String, Object> payload) {
    Map<String, MemberInfoConfigItem> configItems = new LinkedHashMap<>();
    Object cfgObj = payload.get(KEY_CONFIG);
    if (cfgObj instanceof List<?> cfgList) {
      for (Object itemObj : cfgList) {
        if (itemObj instanceof Map<?, ?> itemMap && isEligibleConfigItem(itemMap)) {
          MemberInfoConfigItem item = toConfigItem(itemMap);
          if (item.itemId() != null) {
            configItems.put(item.itemId(), item);
          }
        }
      }
    }
    return configItems;
  }

  private Map<String, MemberInfoConfigItemType> extractConfigItemTypes(
      Map<String, Object> payload) {
    Map<String, MemberInfoConfigItemType> itemTypes = new LinkedHashMap<>();
    Object value = payload.get(KEY_CONFIG_ITEM_TYPES);
    if (value instanceof Map<?, ?> typeMap) {
      for (Map.Entry<?, ?> entry : typeMap.entrySet()) {
        if (entry.getKey() instanceof String itemId
            && entry.getValue() instanceof String itemType
            && isEligibleItemType(itemType)) {
          itemTypes.put(itemId, MemberInfoConfigItemType.fromCode(itemType));
        }
      }
    }
    return itemTypes;
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

  private boolean isEligibleConfigItem(Map<?, ?> itemMap) {
    return isEligibleItemType(itemMap.get("item-type"));
  }

  private boolean isEligibleItemType(Object itemType) {
    if (!(itemType instanceof String text)) {
      return false;
    }
    String normalizedType = text.strip();
    return normalizedType.equalsIgnoreCase("UI") || normalizedType.equalsIgnoreCase("DATA");
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
