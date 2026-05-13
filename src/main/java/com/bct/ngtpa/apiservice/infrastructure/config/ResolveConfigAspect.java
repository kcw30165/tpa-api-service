package com.bct.ngtpa.apiservice.infrastructure.config;

import com.bct.ngtpa.apiservice.shared.config.ConfigLookupContext;
import com.bct.ngtpa.apiservice.shared.config.ConfigLookupRequest;
import com.bct.ngtpa.apiservice.shared.config.ConfigResolutionException;
import com.bct.ngtpa.apiservice.shared.config.ConfigVariantResolver;
import com.bct.ngtpa.apiservice.shared.config.ResolveConfig;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Optional;

@Aspect
@Component
public class ResolveConfigAspect {

    private final ConfigVariantResolver configVariantResolver;
    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public ResolveConfigAspect(ConfigVariantResolver configVariantResolver) {
        this.configVariantResolver = configVariantResolver;
    }

    @Around("@annotation(resolveConfig)")
    public Object resolve(ProceedingJoinPoint joinPoint, ResolveConfig resolveConfig) {
        var method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        var code = resolveCode(method, joinPoint.getArgs(), resolveConfig.code());
        var context = findLookupContext(joinPoint.getArgs());
        var request = new ConfigLookupRequest(resolveConfig.category(), code, context, resolveConfig.required());
        var resolvedValue = configVariantResolver.resolve(request);

        if (Optional.class.equals(method.getReturnType())) {
            return resolvedValue;
        }
        if (String.class.equals(method.getReturnType())) {
            if (resolveConfig.required()) {
                return resolvedValue.orElseThrow(() -> new ConfigResolutionException(
                        "Required config not found for category " + resolveConfig.category() + " and code " + code));
            }
            return resolvedValue.orElse(null);
        }

        throw new ConfigResolutionException(
                "@ResolveConfig methods must return Optional<String> or String: " + method.getDeclaringClass().getSimpleName()
                        + "." + method.getName());
    }

    private String resolveCode(Method method, Object[] args, String expression) {
        if (!expression.startsWith("#")) {
            return expression;
        }

        var context = new MethodBasedEvaluationContext(null, method, args, parameterNameDiscoverer);
        var value = parser.parseExpression(expression).getValue(context);
        return value == null ? null : value.toString();
    }

    private ConfigLookupContext findLookupContext(Object[] args) {
        for (var argument : args) {
            if (argument instanceof ConfigLookupContext lookupContext) {
                return lookupContext;
            }
        }
        throw new ConfigResolutionException("@ResolveConfig requires a ConfigLookupContext argument");
    }
}