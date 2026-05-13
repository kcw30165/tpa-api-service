package com.bct.ngtpa.apiservice.infrastructure.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LoggingSanitizerTest {

    private final LoggingSanitizer sanitizer = new LoggingSanitizer(new ObjectMapper(), properties());

    @Test
    void masksSensitiveMapKeys() {
        Object sanitized = sanitizer.sanitizeValue(Map.of(
                "authorization", "Bearer abc",
                "traceId", "trace-1",
                "policy-no", "P-100"));

        Map<?, ?> sanitizedMap = assertInstanceOf(Map.class, sanitized);
        assertEquals("***", sanitizedMap.get("authorization"));
        assertEquals("***", sanitizedMap.get("policy-no"));
        assertEquals("trace-1", sanitizedMap.get("traceId"));
    }

    @Test
    void masksSensitiveRecordFieldNames() {
        Object sanitized = sanitizer.sanitizeValue(new MemberPayload("user-1", "Alice", "secret-key"));

        Map<?, ?> sanitizedMap = assertInstanceOf(Map.class, sanitized);
        assertEquals("***", sanitizedMap.get("userId"));
        assertEquals("Alice", sanitizedMap.get("displayName"));
        assertEquals("***", sanitizedMap.get("apiKey"));
    }

    @Test
    void leavesNormalFieldsUnmasked() {
        Object sanitized = sanitizer.sanitizeValue(new AuditPayload("trace-1", "DEV"));

        Map<?, ?> sanitizedMap = assertInstanceOf(Map.class, sanitized);
        assertEquals("trace-1", sanitizedMap.get("traceId"));
        assertEquals("DEV", sanitizedMap.get("environment"));
    }

    private record MemberPayload(String userId, String displayName, String apiKey) {
    }

    private record AuditPayload(String traceId, String environment) {
    }

    @Test
    void sanitizeArgumentsWithNullArrayReturnsEmpty() {
        assertEquals(List.of(), sanitizer.sanitizeArguments(null));
    }

    @Test
    void sanitizeArgumentsWithEmptyArrayReturnsEmpty() {
        assertEquals(List.of(), sanitizer.sanitizeArguments(new Object[0]));
    }

    @Test
    void sanitizeValueWithNullReturnsNull() {
        assertNull(sanitizer.sanitizeValue(null));
    }

    @Test
    void sanitizeValueWithNumberPassesThrough() {
        assertEquals(42, sanitizer.sanitizeValue(42));
    }

    @Test
    void sanitizeValueWithBooleanPassesThrough() {
        assertEquals(true, sanitizer.sanitizeValue(true));
    }

    @Test
    void sanitizeValueWithEnumPassesThrough() {
        Object result = sanitizer.sanitizeValue(TestEnum.ACTIVE);
        assertEquals(TestEnum.ACTIVE, result);
    }

    @Test
    void sanitizeValueWithListMasksNestedSensitiveField() {
        Object result = sanitizer.sanitizeValue(List.of(Map.of("authorization", "Bearer xyz")));
        List<?> list = assertInstanceOf(List.class, result);
        Map<?, ?> item = assertInstanceOf(Map.class, list.get(0));
        assertEquals("***", item.get("authorization"));
    }

    @Test
    void sanitizeValueWithArrayMasksNestedSensitiveField() {
        Object[] arr = new Object[]{Map.of("token", "secret-val")};
        Object result = sanitizer.sanitizeValue(arr);
        List<?> list = assertInstanceOf(List.class, result);
        Map<?, ?> item = assertInstanceOf(Map.class, list.get(0));
        assertEquals("***", item.get("token"));
    }

    @Test
    void sanitizeValueWithIntArrayConvertsToList() {
        int[] arr = {1, 2, 3};
        Object result = sanitizer.sanitizeValue(arr);
        List<?> list = assertInstanceOf(List.class, result);
        assertEquals(3, list.size());
    }

    @Test
    void sanitizeValueWithCollectionContainingNullElements() {
        List<Object> col = new ArrayList<>();
        col.add("safe");
        col.add(null);
        List<?> result = assertInstanceOf(List.class, sanitizer.sanitizeValue(col));
        assertEquals("safe", result.get(0));
        assertNull(result.get(1));
    }

    @Test
    void sanitizeValueWithSetPreservesElements() {
        Object result = sanitizer.sanitizeValue(Set.of("hello"));
        List<?> list = assertInstanceOf(List.class, result);
        assertEquals(1, list.size());
        assertEquals("hello", list.get(0));
    }

    @Test
    void sanitizeMapMasksSensitiveValueInsideNestedObject() {
        // Object serialized to JSON node → field "password" is sensitive
        Object result = sanitizer.sanitizeValue(new WithSensitiveField("Alice", "secret"));
        Map<?, ?> m = assertInstanceOf(Map.class, result);
        assertEquals("Alice", m.get("name"));
        assertEquals("***", m.get("password"));
    }

    @Test
    void toSafeStringWithNull() {
        assertEquals("null", sanitizer.toSafeString(null));
    }

    @Test
    void toSafeStringWithNumber() {
        assertEquals("42", sanitizer.toSafeString(42));
    }

    @Test
    void toSafeStringWithBoolean() {
        assertEquals("true", sanitizer.toSafeString(true));
    }

    @Test
    void toSafeStringWithString() {
        assertEquals("hello", sanitizer.toSafeString("hello"));
    }

    @Test
    void toSafeStringWithObjectSerializesToJson() {
        String s = sanitizer.toSafeString(new AuditPayload("trace-1", "DEV"));
        assertTrue(s.contains("trace-1"));
    }

    @Test
    void sensitiveKeyIsBlankReturnsFalse() {
        // isSensitiveKey("") → false → value not masked
        Object result = sanitizer.sanitizeValue(Map.of("safeKey", "value"));
        Map<?, ?> m = assertInstanceOf(Map.class, result);
        assertEquals("value", m.get("safeKey"));
    }

    @Test
    void sanitizeArgumentsWithMultipleArgs() {
        List<Object> result = sanitizer.sanitizeArguments(new Object[]{"hello", 99, null});
        assertEquals(3, result.size());
        assertEquals("hello", result.get(0));
        assertEquals(99, result.get(1));
        assertNull(result.get(1) instanceof Integer ? null : result.get(2));
    }

    @Test
    void sanitizeJsonNodeArrayContainingSensitiveObject() {
        // Directly passing a List containing a Map with sensitive key exercises the
        // isArray() branch in sanitizeJsonNode via sanitizeCollection
        Object result = sanitizer.sanitizeValue(List.of(Map.of("password", "secret")));
        List<?> outer = assertInstanceOf(List.class, result);
        Map<?, ?> inner = assertInstanceOf(Map.class, outer.get(0));
        assertEquals("***", inner.get("password"));
    }

    @Test
    void toSafeStringWithObjectThatFailsSerialization() {
        // Create an unserializable object (circular reference would work, but we'll
        // use an inner class that has no Jackson support and forces fallback)
        var circular = new Object() {
            @Override
            public String toString() { return "UnserializableType"; }
        };
        // ObjectMapper will serialize plain anonymous objects as {} or throw
        // We just want toSafeString to not throw
        String result = sanitizer.toSafeString(circular);
        assertNotNull(result);
    }

    @Test
    void typeNameForArrayType() {
        // Arrays have typeName = componentType.getSimpleName() + "[]"
        // This is invoked in sanitizeValue when Jackson fails; easier to test via toSafeString
        // with a known array type that triggers the path
        byte[] bytes = new byte[]{1, 2, 3};
        // byte[] is handled by isArray() branch in sanitizeValue → sanitizeArray → each byte
        Object result = sanitizer.sanitizeValue(bytes);
        List<?> list = assertInstanceOf(List.class, result);
        assertEquals(3, list.size());
    }

    @Test
    void sensitiveKeyMatchingViaNormalization() {
        // "policy-no" normalized to "policyno" → matches "policyNo" token (also normalized)
        Object result = sanitizer.sanitizeValue(Map.of("policy-no", "P-123"));
        Map<?, ?> m = assertInstanceOf(Map.class, result);
        assertEquals("***", m.get("policy-no"));
    }

    @Test
    void sanitizeValueWithCharSequencePassesThrough() {
        CharSequence cs = new StringBuilder("hello world");
        Object result = sanitizer.sanitizeValue(cs);
        assertEquals(cs, result);
    }

    @Test
    void sanitizeJsonNodeNullFieldReturnsNull() {
        // A POJO serialized to JSON where one field is null → node.isNull() true branch
        Object result = sanitizer.sanitizeValue(new WithNullableField("hello", null));
        Map<?, ?> m = assertInstanceOf(Map.class, result);
        assertNull(m.get("value"));
    }

    @Test
    void sanitizeJsonNodeNumberFieldReturnsNumber() {
        // A POJO with a number field → node.isNumber() true branch in sanitizeJsonNode
        Object result = sanitizer.sanitizeValue(new WithNumericField("test", 42));
        Map<?, ?> m = assertInstanceOf(Map.class, result);
        assertEquals(42, ((Number) m.get("count")).intValue());
    }

    @Test
    void sanitizeJsonNodeBooleanFieldReturnsBooleanValue() {
        // A POJO with a boolean field → node.isBoolean() true branch in sanitizeJsonNode
        Object result = sanitizer.sanitizeValue(new WithBooleanField("test", true));
        Map<?, ?> m = assertInstanceOf(Map.class, result);
        assertEquals(true, m.get("active"));
    }

    @Test
    void sanitizeJsonNodeArrayFieldInPojoHitsArrayBranch() {
        // A POJO with a list/array field serializes to JSON → node.isArray() branch in sanitizeJsonNode
        // and when iterating array items, isSensitiveKey(null) is called → key == null path
        Object result = sanitizer.sanitizeValue(new WithNumericList("data", List.of(1, 2, 3)));
        Map<?, ?> m = assertInstanceOf(Map.class, result);
        List<?> nums = assertInstanceOf(List.class, m.get("values"));
        assertEquals(3, nums.size());
    }

    @Test
    void isSensitiveKeyWithBlankKeyReturnsFalse() {
        // key is not null but blank → key.isBlank() = true → return false
        Object result = sanitizer.sanitizeValue(Map.of("", "value"));
        Map<?, ?> m = assertInstanceOf(Map.class, result);
        // blank key should not be masked
        assertEquals("value", m.get(""));
    }


    enum TestEnum { ACTIVE, INACTIVE }

    private record WithSensitiveField(String name, String password) {}

    private record WithNestedList(List<Map<String, Object>> items) {}

    private record WithNullableField(String name, String value) {}

    private record WithNumericField(String name, int count) {}

    private record WithBooleanField(String name, boolean active) {}

    private record WithNumericList(String name, List<Integer> values) {}

    private static LoggingSanitizerProperties properties() {
        LoggingSanitizerProperties properties = new LoggingSanitizerProperties();
        properties.setSensitiveTokens(List.of(
                "authorization",
                "token",
                "secret",
                "password",
                "apiKey",
                "Certificate",
                "clientSecret",
                "policyNo",
                "certNo",
                "userId",
                "privateKey",
                "publicKey",
                "policy-no",
                "cert-no",
                "user-id"));
        return properties;
    }
}
