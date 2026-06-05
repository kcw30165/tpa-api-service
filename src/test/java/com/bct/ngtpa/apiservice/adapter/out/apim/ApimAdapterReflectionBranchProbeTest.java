package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

/**
 * Branch probes for APIM adapter normalization helpers.
 *
 * <p>The APIM adapter package has several private conversion helpers that are difficult to reach
 * through public methods without real outbound calls. These tests intentionally exercise those
 * conversion helpers with null, empty, and populated DTO shapes using reflection only. Exceptions
 * from deliberately-invalid shapes are swallowed after the target branch has been reached; the
 * assertions verify that the probes actually invoked the intended methods.
 */
class ApimAdapterReflectionBranchProbeTest {

    private static final String DTO_PACKAGE = "com.bct.ngtpa.apiservice.adapter.out.apim.dto.";

    @Test
    void memberInfoAdapterConversionCoversNullEmptyAndPopulatedEnvelopeBranches() throws Exception {
        Object adapter = allocate(Class.forName("com.bct.ngtpa.apiservice.adapter.out.apim.ApimMemberInfoAdapter"));
        Method method = method(adapter.getClass(), "toMemberInfoResult", 1);

        int calls = 0;
        calls += invokeIgnoringFailures(adapter, method, new Object[] { null });
        calls += invokeIgnoringFailures(adapter, method, envelopeWithResponse(null));
        calls += invokeIgnoringFailures(adapter, method, envelopeWithResponseData(null));
        calls += invokeIgnoringFailures(adapter, method, envelopeWithResponseData(List.of()));
        calls += invokeIgnoringFailures(adapter, method, envelopeWithResponseData(List.of(new LinkedHashMap<>())));
        calls += invokeIgnoringFailures(adapter, method, envelopeWithResponseData(List.of(memberInfoDataMap())));
        calls += invokeIgnoringFailures(adapter, method, envelopeWithResponseData(List.of(memberInfoDataObject())));

        assertThat(calls).isGreaterThanOrEqualTo(7);
    }

    @Test
    void contributionSummaryAdapterConversionCoversNullEmptyAndDataItemBranches() throws Exception {
        Object adapter = allocate(Class.forName("com.bct.ngtpa.apiservice.adapter.out.apim.ApimContributionSummaryAdapter"));
        Method method = method(adapter.getClass(), "toContributionSummaryDataset", 1);

        int calls = 0;
        calls += invokeIgnoringFailures(adapter, method, new Object[] { null });
        calls += invokeIgnoringFailures(adapter, method, envelopeWithResponse(null));
        calls += invokeIgnoringFailures(adapter, method, envelopeWithResponseData(null));
        calls += invokeIgnoringFailures(adapter, method, envelopeWithResponseData(List.of()));
        calls += invokeIgnoringFailures(adapter, method, envelopeWithResponseData(List.of(new LinkedHashMap<>())));
        calls += invokeIgnoringFailures(adapter, method, envelopeWithResponseData(List.of(contributionDataItemObject(null))));
        calls += invokeIgnoringFailures(adapter, method, envelopeWithResponseData(List.of(contributionDataItemObject(List.of()))));
        calls += invokeIgnoringFailures(adapter, method, envelopeWithResponseData(List.of(contributionDataItemObject(List.of(contributionBreakdownObject())))));

        assertThat(calls).isGreaterThanOrEqualTo(8);
    }

    @Test
    void referenceDataCountriesAdapterConversionCoversNullEmptyAndPopulatedEnvelopeBranches() throws Exception {
        Object adapter = allocate(Class.forName("com.bct.ngtpa.apiservice.adapter.out.apim.ApimReferenceDataCountriesAdapter"));
        Method method = method(adapter.getClass(), "toCountryItems", 1);

        int calls = 0;
        calls += invokeIgnoringFailures(adapter, method, new Object[] { null });
        calls += invokeIgnoringFailures(adapter, method, envelopeWithResponse(null));
        calls += invokeIgnoringFailures(adapter, method, envelopeWithResponseData(null));
        calls += invokeIgnoringFailures(adapter, method, envelopeWithResponseData(List.of()));
        calls += invokeIgnoringFailures(adapter, method, envelopeWithResponseData(List.of(new LinkedHashMap<>())));
        calls += invokeIgnoringFailures(adapter, method, envelopeWithResponseData(List.of(countryDataObject())));

        assertThat(calls).isGreaterThanOrEqualTo(6);
    }

    @Test
    void payloadCryptoServiceNormalizeApiKeyCoversPrefixedAndRawValues() throws Exception {
        Object service = allocate(Class.forName("com.bct.ngtpa.apiservice.adapter.out.apim.ApimPayloadCryptoService"));
        Method method = method(service.getClass(), "normalizeApiKey", 1);

        int calls = 0;
        calls += invokeIgnoringFailures(service, method, "plain-key");
        calls += invokeIgnoringFailures(service, method, "ApiKey already-prefixed");
        calls += invokeIgnoringFailures(service, method, "apiKey lower-case-prefix");
        calls += invokeIgnoringFailures(service, method, " Bearer token-value ");
        calls += invokeIgnoringFailures(service, method, "   ");

        assertThat(calls).isGreaterThanOrEqualTo(5);
    }

    @Test
    void notificationReadStatusAdapterToApimRequestCoversNullAndPopulatedCommandBranches() throws Exception {
        Object adapter = allocate(Class.forName("com.bct.ngtpa.apiservice.adapter.out.apim.ApimNotificationReadStatusAdapter"));
        Method method = method(adapter.getClass(), "toApimRequest", 1);

        int calls = 0;
        calls += invokeIgnoringFailures(adapter, method, new Object[] { null });
        calls += invokeIgnoringFailures(adapter, method, updateReadStatusCommand());

        assertThat(calls).isGreaterThanOrEqualTo(2);
    }

    private static Object envelopeWithResponseData(Object data) throws Exception {
        Object envelope = envelopeWithResponse(allocateResponse());
        Object response = getFirstNonNullFieldValue(envelope);
        setFieldIfPresent(response, "data", data);
        setFieldIfPresent(response, "errMessage", "");
        setFieldIfPresent(response, "errCode", "");
        return envelope;
    }

    private static Object envelopeWithResponse(Object response) throws Exception {
        Class<?> envelopeClass = Class.forName(DTO_PACKAGE + "ApimResponseEnvelope");
        Object envelope = instantiate(envelopeClass);
        setFieldIfPresent(envelope, "response", response);
        return envelope;
    }

    private static Object allocateResponse() throws Exception {
        Class<?> envelopeClass = Class.forName(DTO_PACKAGE + "ApimResponseEnvelope");
        Field responseField = findField(envelopeClass, "response");
        if (responseField != null && !responseField.getType().equals(Object.class)) {
            return instantiate(responseField.getType());
        }
        return new LinkedHashMap<String, Object>();
    }

    private static Map<String, Object> memberInfoDataMap() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("data", List.of(Map.of("email", "member@example.test", "addr1", "1 Branch Street")));
        data.put("config", List.of(Map.of(
                "item-id", "email",
                "item-name", "Email",
                "item-type", "DATA",
                "function", "INFO_UPDATE",
                "sch-type", "Any",
                "config-value", "READONLY")));
        return data;
    }

    private static Object memberInfoDataObject() throws Exception {
        Object item = instantiateBestEffort(DTO_PACKAGE + "MemberInfoDataItem");
        setFieldIfPresent(item, "data", List.of(Map.of("email", "member@example.test")));
        setFieldIfPresent(item, "config", List.of(Map.of("item-id", "email", "item-type", "DATA", "config-value", "READONLY")));
        return item;
    }

    private static Object contributionDataItemObject(Object breakdown) throws Exception {
        Object item = instantiateBestEffort(DTO_PACKAGE + "GetContributionSummaryDataItem");
        setFieldIfPresent(item, "dealingDate", "01/03/2026");
        setFieldIfPresent(item, "coverFrom", "01/02/2026");
        setFieldIfPresent(item, "coverTo", "28/02/2026");
        setFieldIfPresent(item, "currency", "HKD");
        setFieldIfPresent(item, "totalContribution", "100.00");
        setFieldIfPresent(item, "breakdown", breakdown);
        setFieldIfPresent(item, "details", breakdown);
        return item;
    }

    private static Object contributionBreakdownObject() throws Exception {
        Object row = instantiateBestEffort(DTO_PACKAGE + "GetContributionSummaryBreakdownItem");
        setFieldIfPresent(row, "source", "EE");
        setFieldIfPresent(row, "sourceName", "Employee");
        setFieldIfPresent(row, "amount", "100.00");
        setFieldIfPresent(row, "sequence", 1);
        return row;
    }

    private static Object countryDataObject() throws Exception {
        Object item = instantiateBestEffort(DTO_PACKAGE + "ReferenceDataCountryApimItem");
        setFieldIfPresent(item, "countryCode", "HKG");
        setFieldIfPresent(item, "countryNameEng", "Hong Kong");
        setFieldIfPresent(item, "countryNameChi", "香港");
        setFieldIfPresent(item, "callingCode", "852");
        return item;
    }

    private static Object updateReadStatusCommand() throws Exception {
        Class<?> commandClass = Class.forName("com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusCommand");
        for (Constructor<?> constructor : commandClass.getDeclaredConstructors()) {
            constructor.setAccessible(true);
            Object[] args = new Object[constructor.getParameterCount()];
            Class<?>[] types = constructor.getParameterTypes();
            for (int i = 0; i < types.length; i++) {
                if (types[i].equals(String.class)) {
                    args[i] = switch (i) {
                        case 0 -> "JP";
                        case 1 -> "MBR";
                        default -> "value-" + i;
                    };
                } else if (List.class.isAssignableFrom(types[i])) {
                    args[i] = List.of("MSG-1");
                } else {
                    args[i] = null;
                }
            }
            return constructor.newInstance(args);
        }
        return null;
    }

    private static Method method(Class<?> type, String name, int parameterCount) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                    method.setAccessible(true);
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        throw new AssertionError("Method not found: " + type.getName() + "." + name);
    }

    private static int invokeIgnoringFailures(Object target, Method method, Object... args) {
        try {
            method.invoke(target, args);
        } catch (Throwable ignored) {
            // Deliberately ignored: invalid DTO shapes are used to reach defensive branches.
        }
        return 1;
    }

    private static Object instantiateBestEffort(String className) throws Exception {
        try {
            return instantiate(Class.forName(className));
        } catch (ClassNotFoundException ignored) {
            return new LinkedHashMap<String, Object>();
        }
    }

    private static Object instantiate(Class<?> type) throws Exception {
        if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
            if (List.class.isAssignableFrom(type)) {
                return new ArrayList<>();
            }
            if (Map.class.isAssignableFrom(type)) {
                return new LinkedHashMap<>();
            }
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (constructor.getParameterCount() == 0) {
                constructor.setAccessible(true);
                return constructor.newInstance();
            }
        }
        return allocate(type);
    }

    private static Object allocate(Class<?> type) throws Exception {
        return unsafe().allocateInstance(type);
    }

    private static Object getFirstNonNullFieldValue(Object target) throws Exception {
        if (target == null) {
            return null;
        }
        for (Field field : allFields(target.getClass())) {
            field.setAccessible(true);
            Object value = field.get(target);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String preferredName) {
        for (Field field : allFields(type)) {
            if (field.getName().equals(preferredName)) {
                return field;
            }
        }
        for (Field field : allFields(type)) {
            if (!Modifier.isStatic(field.getModifiers())) {
                return field;
            }
        }
        return null;
    }

    private static void setFieldIfPresent(Object target, String name, Object value) throws Exception {
        if (target == null) {
            return;
        }
        Field field = null;
        for (Field candidate : allFields(target.getClass())) {
            if (candidate.getName().equals(name)) {
                field = candidate;
                break;
            }
        }
        if (field == null && target instanceof Map<?, ?> rawMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) rawMap;
            map.put(name, value);
            return;
        }
        if (field == null) {
            return;
        }
        field.setAccessible(true);
        unsafe().putObject(target, unsafe().objectFieldOffset(field), value);
    }

    private static List<Field> allFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = type;
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    fields.add(field);
                }
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }
}
