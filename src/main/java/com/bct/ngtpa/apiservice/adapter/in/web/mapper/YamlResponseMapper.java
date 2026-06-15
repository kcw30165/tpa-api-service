package com.bct.ngtpa.apiservice.adapter.in.web.mapper;
import java.util.Comparator;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class YamlResponseMapper {

    private static final String DEFAULT_LANGUAGE = "en";
    private static final String ZH_HK_LANGUAGE = "zh_HK";

    private final ObjectMapper objectMapper;

    public YamlResponseMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> toResponseMap(Object source, String language) {
        if (source == null) {
            return Map.of();
        }
        Map<String, Object> raw = objectMapper.convertValue(source, new TypeReference<>() {
        });
        Object normalized = normalize(raw, language);
        if (!(normalized instanceof Map<?, ?> normalizedMap)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : normalizedMap.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return normalizeResponseMap(result);
    }

    public List<Map<String, Object>> toResponseList(List<?> source, String language) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : source) {
            Map<String, Object> mapped = toResponseMap(item, language);
            if (!mapped.isEmpty()) {
                result.add(mapped);
            }
        }
        return List.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private Object normalize(Object value, String language) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            if (looksLikeLocalizedLabel(map)) {
                return resolveLabel((Map<String, String>) map, language);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (shouldSkipKey(key)) {
                    continue;
                }
                Object normalizedValue = normalize(entry.getValue(), language);
                if (!isEmptyValue(normalizedValue)) {
                    result.put(key, normalizedValue);
                }
            }
            return normalizeResponseMap(result);
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            for (Object item : list) {
                Object normalizedItem = normalize(item, language);
                if (!isEmptyValue(normalizedItem)) {
                    result.add(normalizedItem);
                }
            }
            return normalizeResponseMap(result);
        }
        return value;
    }

    private boolean looksLikeLocalizedLabel(Map<?, ?> value) {
        return value.containsKey("en") || value.containsKey("zh_HK") || value.containsKey("zh-HK");
    }

    private String resolveLabel(Map<String, String> labels, String language) {
        if (labels == null || labels.isEmpty()) {
            return "";
        }
        String label = labels.get(language);
        if (label == null && ZH_HK_LANGUAGE.equals(language)) {
            label = labels.get("zh-HK");
        }
        if (label == null) {
            label = labels.get(DEFAULT_LANGUAGE);
        }
        if (label == null) {
            label = labels.values().iterator().next();
        }
        return label != null ? label : "";
    }

    private boolean shouldSkipKey(String key) {
        return isHiddenResponseKey(key);
    }

    private boolean isEmptyValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return text.isBlank();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        if (value instanceof List<?> list) {
            return list.isEmpty();
        }
        return false;
    }



    private List<Object> normalizeResponseMap(List<Object> source) {
        if (source == null || source.isEmpty()) {
            return source;
        }
        List<Object> normalized = new ArrayList<>();
        for (Object item : source) {
            Object normalizedItem = normalizeResponseValue(item);
            if (normalizedItem != null) {
                normalized.add(normalizedItem);
            }
        }
        return normalized;
    }

    private Map<String, Object> normalizeResponseMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return source;
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (isHiddenResponseKey(entry.getKey())) {
                continue;
            }
            Object normalizedValue = normalizeResponseValue(entry.getValue());
            if (normalizedValue != null) {
                normalized.put(entry.getKey(), normalizedValue);
            }
        }
        return normalized;
    }

    private Object normalizeResponseValue(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() != null) {
                    map.put(entry.getKey().toString(), entry.getValue());
                }
            }
            if (isNumericIndexedMap(map)) {
                List<Object> values = new ArrayList<>();
                map.entrySet().stream()
                        .sorted(Comparator.comparingInt(entry -> Integer.parseInt(entry.getKey())))
                        .forEach(entry -> values.add(normalizeResponseValue(entry.getValue())));
                return values;
            }
            return normalizeResponseMap(map);
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>();
            for (Object item : list) {
                Object normalizedItem = normalizeResponseValue(item);
                if (normalizedItem != null) {
                    normalized.add(normalizedItem);
                }
            }
            return normalized;
        }
        return value;
    }

    private boolean isNumericIndexedMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return false;
        }
        for (String key : map.keySet()) {
            if (key == null || !key.matches("\\d+")) {
                return false;
            }
        }
        return true;
    }

    private boolean isHiddenResponseKey(String key) {
        return Set.of("apimBinding", "messageCode", "labelCode", "placeholderCode").contains(key);
    }

}
