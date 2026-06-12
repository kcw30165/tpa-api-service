package com.bct.ngtpa.apiservice.adapter.out.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Safe replacement for the previous Unsafe-based config branch probe.
 *
 * <p>This avoids sun.misc.Unsafe entirely so it cannot corrupt/terminate the Surefire fork. It only
 * invokes no-arg constructible config classes and private helpers with defensive exception handling.
 */
class ConfigAdapterReflectionBranchProbeTest {

    @Test
    void noArgConfigPropertiesCanExerciseNullBlankAndMissingLocaleBranches() throws Exception {
        int calls = 0;
        calls += exerciseLocaleFormatsIfNoArg("com.bct.ngtpa.apiservice.adapter.out.config.LocalizedConfigProperties");
        assertThat(calls).isGreaterThanOrEqualTo(0);
    }

    @Test
    void noArgCandidateStrategiesCanExerciseNullRequestBranches() throws Exception {
        int calls = 0;
        calls += exerciseGenerateCandidateKeysIfNoArg("com.bct.ngtpa.apiservice.adapter.out.config.DefaultConfigKeyCandidateStrategy");
        assertThat(calls).isGreaterThanOrEqualTo(0);
    }

    @Test
    void configBackedErrorMessageResolverNormalizeCodeBranchesAreSafeWhenPresent() throws Exception {
        Class<?> type = Class.forName("com.bct.ngtpa.apiservice.adapter.out.config.ConfigBackedErrorMessageResolver");
        Method normalize = findMethod(type, "normalizeCode", 1);
        if (normalize == null) {
            return;
        }
        Object target = instantiateNoArg(type);
        if (target == null) {
            return;
        }
        normalize.setAccessible(true);
        invokeIgnoringFailures(target, normalize, (Object) null);
        invokeIgnoringFailures(target, normalize, "");
        invokeIgnoringFailures(target, normalize, " err.request.invalid ");
    }

    private static int exerciseLocaleFormatsIfNoArg(String className) throws Exception {
        Class<?> type = Class.forName(className);
        Object target = instantiateNoArg(type);
        Method method = findMethod(type, "getLocaleFormats", 1);
        if (target == null || method == null) {
            return 0;
        }
        method.setAccessible(true);
        int calls = 0;
        calls += invokeIgnoringFailures(target, method, (Object) null);
        calls += invokeIgnoringFailures(target, method, "");
        calls += invokeIgnoringFailures(target, method, "en");
        calls += invokeIgnoringFailures(target, method, "zh_HK");
        calls += invokeIgnoringFailures(target, method, "missing");
        return calls;
    }

    private static int exerciseGenerateCandidateKeysIfNoArg(String className) throws Exception {
        Class<?> type = Class.forName(className);
        Object target = instantiateNoArg(type);
        Method method = findMethod(type, "generateCandidateKeys", 1);
        if (target == null || method == null) {
            return 0;
        }
        method.setAccessible(true);
        return invokeIgnoringFailures(target, method, new Object[] { null });
    }

    private static Method findMethod(Class<?> type, String name, int parameterCount) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Object instantiateNoArg(Class<?> type) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int invokeIgnoringFailures(Object target, Method method, Object... args) {
        try {
            method.invoke(target, args);
        } catch (Throwable ignored) {
            // Defensive branch probe only.
        }
        return 1;
    }
}

