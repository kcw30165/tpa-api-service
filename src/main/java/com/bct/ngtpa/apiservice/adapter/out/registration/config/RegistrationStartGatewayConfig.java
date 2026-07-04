package com.bct.ngtpa.apiservice.adapter.out.registration.config;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengePort;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.RegistrationCsrfGateway;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.RegistrationMessageGateway;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.RegistrationOtpCreationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.RegistrationOtpGateway;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.RegistrationSessionGateway;
import com.bct.ngtpa.apiservice.adapter.out.registration.RegistrationAuthFlowCsrfGatewayAdapter;
import com.bct.ngtpa.apiservice.adapter.out.registration.RegistrationAuthFlowSessionGatewayAdapter;
import com.bct.ngtpa.apiservice.adapter.out.registration.RegistrationOtpChallengeGatewayAdapter;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionCsrfService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionLifecycleService;
import org.springframework.beans.factory.annotation.Value;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RegistrationStartGatewayConfig {

    @Bean
    @ConditionalOnMissingBean
    public RegistrationSessionGateway registrationSessionGateway(
            AuthFlowSessionLifecycleService lifecycleService,
            @Value("${auth-flow-session.scenarios.registration.ttl-seconds:1800}") int registrationTtlSeconds,
            @Value("${auth-flow-session.session-id.prefix:afs}") String sessionIdPrefix,
            @Value("${auth-flow-session.session-id.entropy-bytes:16}") int sessionIdEntropyBytes) {
        return new RegistrationAuthFlowSessionGatewayAdapter(
                lifecycleService,
                registrationTtlSeconds,
                sessionIdPrefix,
                sessionIdEntropyBytes);
    }

    @Bean
    @ConditionalOnMissingBean
    public RegistrationCsrfGateway registrationCsrfGateway(
            AuthFlowSessionCsrfService csrfService,
            Clock clock,
            @Value("${auth-flow-session.csrf.max-age-seconds:1800}") int csrfTtlSeconds) {
        return new RegistrationAuthFlowCsrfGatewayAdapter(
                csrfService,
                clock,
                csrfTtlSeconds);
    }

    @Bean
    @ConditionalOnMissingBean
    public RegistrationOtpGateway registrationOtpGateway(
            OtpChallengePort otpChallengePort,
            AuthWorkflowPolicyPort authWorkflowPolicyPort,
            Clock clock) {
        return new RegistrationOtpChallengeGatewayAdapter(
                otpChallengePort,
                authWorkflowPolicyPort,
                clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public RegistrationMessageGateway registrationMessageGateway(ApplicationContext applicationContext) {
        return new ReflectiveRegistrationMessageGateway(applicationContext);
    }

    private static final class ReflectiveRegistrationMessageGateway implements RegistrationMessageGateway {
        private final ApplicationContext applicationContext;

        private ReflectiveRegistrationMessageGateway(ApplicationContext applicationContext) {
            this.applicationContext = applicationContext;
        }

        @Override
        public Mono<Boolean> deliverRegistrationOtp(RegistrationContactType contactType, String recipient,
                String otpCode,
                int ttlSeconds, String acceptLanguage, String correlationId) {
            Object port = firstBean(applicationContext,
                    "com.bct.ngtpa.apiservice.application.port.out.MessageDeliveryPort");
            if (port == null) {
                return Mono.error(
                        new IllegalStateException("MessageDeliveryPort bean is not available for registration/start"));
            }
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("code", otpCode);
            variables.put("ttlValue", String.valueOf(Math.max(1, ttlSeconds / 60)));
            variables.put("ttlUnit", "minutes");
            Object command = tryCreateCommand(
                    "com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryCommand",
                    "REGISTRATION_OTP",
                    "REGISTRATION",
                    contactType == null ? null : contactType.name(),
                    recipient,
                    "BCT verification code",
                    "Your verification code is {code}. It is valid for {ttlValue} {ttlUnit}.",
                    "registration-otp",
                    variables,
                    acceptLanguage,
                    correlationId);
            if (command == null) {
                return Mono.error(
                        new IllegalStateException("Unable to create MessageDeliveryCommand for registration/start"));
            }
            return invokeMono(port, "deliver", command)
                    .map(result -> firstBoolean(result, true, "accepted", "success", "delivered"));
        }
    }

    private static Object firstBean(ApplicationContext context, String... classNames) {
        for (String className : classNames) {
            try {
                Class<?> type = Class.forName(className);
                String[] names = context.getBeanNamesForType(type);
                if (names.length > 0) {
                    return context.getBean(names[0]);
                }
            } catch (ClassNotFoundException ignored) {
                // Optional integration point not present in this branch.
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> Mono<T> invokeMono(Object target, String methodName, Object command) {
        try {
            for (Method method : target.getClass().getMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == 1
                        && method.getParameterTypes()[0].isAssignableFrom(command.getClass())) {
                    Object result = method.invoke(target, command);
                    if (result instanceof Mono<?> mono) {
                        return (Mono<T>) mono;
                    }
                    return Mono.just((T) result);
                }
            }
            return Mono.error(new IllegalStateException(
                    "No compatible method " + methodName + " found on " + target.getClass().getName()));
        } catch (ReflectiveOperationException exception) {
            return Mono.error(exception);
        }
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
