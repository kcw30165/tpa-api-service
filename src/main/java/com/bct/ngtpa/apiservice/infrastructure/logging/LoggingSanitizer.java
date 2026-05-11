package com.bct.ngtpa.apiservice.infrastructure.logging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class LoggingSanitizer {

    private static final String MASKED_VALUE = "***";

    private final ObjectMapper objectMapper;
    private final LoggingSanitizerProperties properties;

    public LoggingSanitizer(ObjectMapper objectMapper, LoggingSanitizerProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public List<Object> sanitizeArguments(Object[] args) {
        if (args == null || args.length == 0) {
            return List.of();
        }

        List<Object> sanitized = new ArrayList<>(args.length);
        for (Object arg : args) {
            sanitized.add(sanitizeValue(arg));
        }
        return sanitized;
    }

    public Object sanitizeValue(Object value) {
        return sanitizeValue(null, value);
    }

    public String toSafeString(Object value) {
        Object sanitized = sanitizeValue(value);
        if (sanitized == null) {
            return "null";
        }
        if (sanitized instanceof CharSequence || sanitized instanceof Number || sanitized instanceof Boolean) {
            return sanitized.toString();
        }

        try {
            return objectMapper.writeValueAsString(sanitized);
        } catch (Exception ex) {
            return typeName(value);
        }
    }

    private Object sanitizeValue(String fieldName, Object value) {
        if (isSensitiveKey(fieldName)) {
            return MASKED_VALUE;
        }
        if (value == null) {
            return null;
        }
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean || value instanceof Enum<?>) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            return sanitizeMap(map);
        }
        if (value instanceof Collection<?> collection) {
            return sanitizeCollection(collection);
        }
        if (value.getClass().isArray()) {
            return sanitizeArray(value);
        }

        try {
            JsonNode tree = objectMapper.valueToTree(value);
            return sanitizeJsonNode(fieldName, tree);
        } catch (Exception ex) {
            return typeName(value);
        }
    }

    private Map<String, Object> sanitizeMap(Map<?, ?> map) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        map.forEach((key, value) -> {
            String keyName = String.valueOf(key);
            sanitized.put(keyName, sanitizeValue(keyName, value));
        });
        return sanitized;
    }

    private List<Object> sanitizeCollection(Collection<?> collection) {
        List<Object> sanitized = new ArrayList<>(collection.size());
        for (Object item : collection) {
            sanitized.add(sanitizeValue(item));
        }
        return sanitized;
    }

    private List<Object> sanitizeArray(Object array) {
        int length = Array.getLength(array);
        List<Object> sanitized = new ArrayList<>(length);
        for (int index = 0; index < length; index++) {
            sanitized.add(sanitizeValue(Array.get(array, index)));
        }
        return sanitized;
    }

    private Object sanitizeJsonNode(String fieldName, JsonNode node) {
        if (isSensitiveKey(fieldName)) {
            return MASKED_VALUE;
        }
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            node.properties().forEach(entry ->
                sanitized.put(entry.getKey(), sanitizeJsonNode(entry.getKey(), entry.getValue())));
            return sanitized;
        }
        if (node.isArray()) {
            List<Object> sanitized = new ArrayList<>(node.size());
            node.forEach(item -> sanitized.add(sanitizeJsonNode(null, item)));
            return sanitized;
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        return node.asText();
    }

    private boolean isSensitiveKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }

        String normalized = key.replaceAll("[^A-Za-z0-9]", "")
                .toLowerCase(Locale.ROOT);
        return properties.getSensitiveTokens().stream()
                .map(token -> token.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT))
                .anyMatch(normalized::contains);
    }

    private String typeName(Object value) {
        if (value == null) {
            return "null";
        }
        if (value.getClass().isArray()) {
            return value.getClass().getComponentType().getSimpleName() + "[]";
        }
        return value.getClass().getSimpleName();
    }
}
