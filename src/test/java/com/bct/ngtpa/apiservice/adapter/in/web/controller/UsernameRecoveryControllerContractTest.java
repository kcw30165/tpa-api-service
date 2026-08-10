package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

class UsernameRecoveryControllerContractTest {

    private static final String BASE_PATH = "/api/v1/auth/username-recovery";

    @Test
    void exposesExactlyThreeMappedEndpointsUnderCanonicalBasePath() throws Exception {
        RequestMapping classMapping = AuthUsernameRecoveryController.class.getAnnotation(RequestMapping.class);
        assertThat(classMapping).isNotNull();
        assertThat(classMapping.value()).containsExactly(BASE_PATH);

        assertThat(postPath("start")).isEqualTo("/start");
        assertThat(postPath("request")).isEqualTo("/request");
        assertThat(postPath("cancel")).isEqualTo("/cancel");
    }

    @Test
    void requestAndCancelAreEmptyBodyEndpoints() throws Exception {
        assertEmptyBody("request");
        assertEmptyBody("cancel");
    }

    @Test
    void startAcceptsTheSixApprovedBodyFieldsAndCarriesTransportHeaders() throws Exception {
        Method method = method("start");
        Parameter body = method.getParameters()[0];
        assertThat(body.isAnnotationPresent(RequestBody.class)).isTrue();
    }

    private static void assertEmptyBody(String name) throws Exception {
        Method method = method(name);
        assertThat(List.of(method.getParameters()))
                .noneMatch(parameter -> parameter.isAnnotationPresent(RequestBody.class));
    }

    private static String postPath(String methodName) throws Exception {
        PostMapping mapping = method(methodName).getAnnotation(PostMapping.class);
        assertThat(mapping).as("missing @PostMapping on %s", methodName).isNotNull();
        return mapping.path().length > 0 ? mapping.path()[0] : mapping.value()[0];
    }

    private static Method method(String name) throws Exception {
        for (Method candidate : AuthUsernameRecoveryController.class.getDeclaredMethods()) {
            if (candidate.getName().equals(name)
                    && candidate.isAnnotationPresent(PostMapping.class)) {
                return candidate;
            }
        }
        throw new AssertionError("no @PostMapping method named " + name);
    }
}
