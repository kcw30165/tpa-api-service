package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.port.in.CreateAuthFlowSessionCsrfUseCase;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionCsrfService;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043CreateAuthFlowSessionCsrfCapabilityTest {

    @Test
    void createCapabilityIsNarrowAndFunctional() {
        assertThat(CreateAuthFlowSessionCsrfUseCase.class.isAnnotationPresent(FunctionalInterface.class))
                .isTrue();
        assertThat(CreateAuthFlowSessionCsrfUseCase.class.getDeclaredMethods())
                .extracting(Method::getName)
                .containsExactly("createForSession");
    }

    @Test
    void sharedServiceImplementsCreateCapabilityWithoutLosingRotateOrValidate() {
        assertThat(CreateAuthFlowSessionCsrfUseCase.class)
                .isAssignableFrom(AuthFlowSessionCsrfService.class);
        assertThat(AuthFlowSessionCsrfService.class.getDeclaredMethods())
                .extracting(Method::getName)
                .contains("createForSession", "rotateForSession", "validateForSession");
    }

    @Test
    void loginStartDependsOnlyOnCreateCapability() throws Exception {
        String service = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/start/StartLoginService.java"));
        String config = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/start/StartLoginConfig.java"));

        assertThat(service).contains("CreateAuthFlowSessionCsrfUseCase");
        assertThat(service).doesNotContain("AuthFlowSessionCsrfService");
        assertThat(config).contains("CreateAuthFlowSessionCsrfUseCase");
    }
}
