package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationStartRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthRegistrationController;

class AuthRegistrationControllerStartTest {
    // @Test
    // void registrationStartEndpointExists() throws Exception {
    //     RequestMapping classMapping = AuthRegistrationController.class.getAnnotation(RequestMapping.class);
    //     Method method = AuthRegistrationController.class.getDeclaredMethod("startRegistration",
    //             RegistrationStartRequest.class,
    //             String.class, String.class, String.class, String.class,
    //             ServerHttpResponse.class);
    //     PostMapping postMapping = method.getAnnotation(PostMapping.class);

    //     assertThat(classMapping.value()).containsExactly("/api/v1/auth/registration");
    //     assertThat(postMapping.value()).containsExactly("/start");
    // }

    @Test
    void p6_7DoesNotDeclareOtherRegistrationEndpointMethods() {
        assertThat(java.util.Arrays.stream(AuthRegistrationController.class.getDeclaredMethods()).map(Method::getName))
                .contains("startRegistration")
                .doesNotContain("resendOtp", "verifyOtp", "checkUsername", "completeRegistration",
                        "cancelRegistration");
    }
}
