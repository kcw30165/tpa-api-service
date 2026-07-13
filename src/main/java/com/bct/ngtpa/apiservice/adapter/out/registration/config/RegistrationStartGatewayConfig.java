package com.bct.ngtpa.apiservice.adapter.out.registration.config;

import com.bct.ngtpa.apiservice.application.port.out.RegistrationAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationCsrPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationMessagePort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationOtpPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import com.bct.ngtpa.apiservice.application.port.out.MessageDeliveryPort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengePort;
import com.bct.ngtpa.apiservice.adapter.out.registration.RegistrationAuthFlowCsrfAdapter;
import com.bct.ngtpa.apiservice.adapter.out.registration.RegistrationAuthFlowSessionAdapter;
import com.bct.ngtpa.apiservice.adapter.out.registration.RegistrationMessageDeliveryAdapter;
import com.bct.ngtpa.apiservice.adapter.out.registration.RegistrationMessageDestinationOverrideAdapter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.infrastructure.environment.DeploymentEnvironmentPolicy;
import com.bct.ngtpa.apiservice.adapter.out.registration.RegistrationOtpChallengeAdapter;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionCsrfService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionLifecycleService;
import com.bct.ngtpa.apiservice.adapter.in.web.session.config.AuthFlowSessionProperties;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RegistrationStartGatewayConfig {

    @Bean
    @ConditionalOnMissingBean
    public RegistrationSessionPort registrationSessionGateway(
            AuthFlowSessionLifecycleService lifecycleService,
            AuthFlowSessionProperties authFlowSessionProperties,
            RegistrationAuthFlowContextProtectorPort registrationAuthFlowContextProtector) {
        return new RegistrationAuthFlowSessionAdapter(
                lifecycleService,
                authFlowSessionProperties.getScenarios().getRegistration().getTtlSeconds(),
                authFlowSessionProperties.getSessionId().getPrefix(),
                authFlowSessionProperties.getSessionId().getEntropyBytes(),
                registrationAuthFlowContextProtector);
    }

    @Bean
    @ConditionalOnMissingBean
    public RegistrationCsrPort registrationCsrfGateway(
            AuthFlowSessionCsrfService csrfService,
            Clock clock,
            AuthFlowSessionProperties authFlowSessionProperties) {
        return new RegistrationAuthFlowCsrfAdapter(
                csrfService,
                clock,
                authFlowSessionProperties.getCsrf().getMaxAgeSeconds());
    }

    @Bean
    @ConditionalOnMissingBean
    public RegistrationOtpPort registrationOtpGateway(
            OtpChallengePort otpChallengePort,
            AuthWorkflowPolicyPort authWorkflowPolicyPort,
            Clock clock) {
        return new RegistrationOtpChallengeAdapter(
                otpChallengePort,
                authWorkflowPolicyPort,
                clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public RegistrationMessagePort registrationMessageGateway(
            MessageDeliveryPort messageDeliveryPort,
            AuthWorkflowPolicyPort authWorkflowPolicyPort,
            DeploymentEnvironmentPolicy deploymentEnvironmentPolicy,
            AuthWorkflowProperties authWorkflowProperties) {
        var deliveryAdapter = new RegistrationMessageDeliveryAdapter(
                messageDeliveryPort,
                authWorkflowPolicyPort);
        if (deploymentEnvironmentPolicy.isNonProductionLikeDeployment()) {
            requiredDestinationOverride(authWorkflowProperties).validateRequiredDestinations();
        }
        return new RegistrationMessageDestinationOverrideAdapter(
                deliveryAdapter,
                deploymentEnvironmentPolicy,
                authWorkflowProperties);
    }

    private AuthWorkflowProperties.DestinationOverride requiredDestinationOverride(
            AuthWorkflowProperties authWorkflowProperties) {
        if (authWorkflowProperties == null || authWorkflowProperties.getRegistration() == null) {
            throw new IllegalStateException("auth-workflows.registration configuration is required");
        }
        return authWorkflowProperties.getRegistration().getMessage().getDestinationOverride();
    }

    private static Object tryCreateCommand(String className, Object... values) {
        try {
            Class<?> type = Class.forName(className);
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                if (constructor.getParameterCount() != values.length) {
                    continue;
                }
                Object[] converted = new Object[values.length];
                Class<?>[] parameterTypes = constructor.getParameterTypes();
                boolean compatible = true;
                for (int i = 0; i < values.length; i++) {
                    converted[i] = convert(values[i], parameterTypes[i]);
                    if (values[i] != null && converted[i] == null
                            && !parameterTypes[i].isAssignableFrom(values[i].getClass())) {
                        compatible = false;
                        break;
                    }
                }
                if (!compatible) {
                    continue;
                }
                constructor.setAccessible(true);
                return constructor.newInstance(converted);
            }
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
        return null;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Object convert(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return value;
        }
        if (targetType == String.class) {
            return String.valueOf(value);
        }
        if ((targetType == int.class || targetType == Integer.class) && value instanceof Number number) {
            return number.intValue();
        }
        if ((targetType == boolean.class || targetType == Boolean.class) && value instanceof Boolean bool) {
            return bool;
        }
        if (targetType.isEnum()) {
            return Enum.valueOf((Class<Enum>) targetType.asSubclass(Enum.class), String.valueOf(value));
        }
        if (targetType.getSimpleName().endsWith("Id") && value instanceof String text) {
            Object id = tryCreateCommand(targetType.getName(), text);
            if (id != null) {
                return id;
            }
        }
        return value;
    }

    private static String extractSessionIdValue(Object session) {
        Object id = firstValue(session, "authFlowSessionId", "sessionId", "id");
        return id == null ? null : firstText(id, "value", "id", id.toString());
    }

    private static String extractCsrfTokenValue(Object result) {
        return firstText(result, "csrfToken", "token", "value", null);
    }

    private static Object firstValue(Object target, String... accessors) {
        if (target == null) {
            return null;
        }
        for (String accessor : accessors) {
            try {
                Method method = target.getClass().getMethod(accessor);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
                // Try next accessor.
            }
            String beanGetter = "get" + Character.toUpperCase(accessor.charAt(0)) + accessor.substring(1);
            try {
                Method method = target.getClass().getMethod(beanGetter);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
                // Try next accessor.
            }
        }
        return null;
    }

    private static String firstText(Object target, String a, String b, String c, String fallback) {
        return firstText(target, new String[] { a, b, c }, fallback);
    }

    private static String firstText(Object target, String a, String b, String fallback) {
        return firstText(target, new String[] { a, b }, fallback);
    }

    private static String firstText(Object target, String[] accessors, String fallback) {
        Object value = firstValue(target, accessors);
        return value == null ? fallback : String.valueOf(value);
    }

    private static int firstInt(Object target, int fallback, String... accessors) {
        Object value = firstValue(target, accessors);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static boolean firstBoolean(Object target, boolean fallback, String... accessors) {
        Object value = firstValue(target, accessors);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }
}
