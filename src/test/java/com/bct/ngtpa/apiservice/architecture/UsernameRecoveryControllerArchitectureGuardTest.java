package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UsernameRecoveryControllerArchitectureGuardTest {

    private static final String CONTROLLER =
            "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthUsernameRecoveryController.java";

    @Test
    void controllerDelegatesThroughCommittedPortsAndTask023PoliciesOnly() throws Exception {
        String source = Files.readString(Path.of(CONTROLLER));

        assertThat(source).contains(
                "StartUsernameRecoveryUseCase",
                "RequestUsernameRecoveryUseCase",
                "CancelUsernameRecoveryUseCase",
                "UsernameRecoveryStartResponsePolicy",
                "UsernameRecoveryRequestResponsePolicy",
                "UsernameRecoveryCancelResponsePolicy",
                "UsernameRecoveryStartWebMapper",
                "UsernameRecoveryRequestWebMapper",
                "UsernameRecoveryCancelWebMapper",
                "UsernameRecoveryCookieWriter");
    }

    @Test
    void controllerUsesSharedHelpersAndNeverInspectsErrorStrings() throws Exception {
        String source = Files.readString(Path.of(CONTROLLER));

        assertThat(source).contains(
                "WorkflowHttpResponses.toResponse",
                "CookieHttpResponses.missingCookie",
                "CsrfHttpResponses.missingToken",
                "IdempotencyHttpResponses.missingKey",
                "RequestHeaderHttpResponses.missingRequiredHeader");

        // No string-based HTTP classification; no error-message inspection.
        assertThat(source).doesNotContain("getMessage()", ".contains(", "switch (errorCode)",
                "errorCode.equals", "error.getMessage");
    }

    @Test
    void controllerHasNoDependencyOnConcreteServicesRedisApimConfigOrOtherWorkflowControllers()
            throws Exception {
        String source = Files.readString(Path.of(CONTROLLER));

        assertThat(source).doesNotContain(
                "adapter.out",
                "RedisTemplate",
                "ReactiveStringRedisTemplate",
                "infrastructure",
                "UsernameRecoveryFeatureConfig",
                "StartUsernameRecoveryService",
                "RequestUsernameRecoveryService",
                "CancelUsernameRecoveryService",
                "AuthLogin",
                "AuthRegistration",
                "AuthCaptcha",
                "AuthLogout",
                "AuthForgotPassword");
    }

    @Test
    void controllerDoesNotHandBuildCookiesAndKeepsRawCsrfOutOfCommands() throws Exception {
        String source = Files.readString(Path.of(CONTROLLER));

        assertThat(source).contains("HttpSessionCookieAdapter", "cookieWriter");
        assertThat(source).doesNotContain("ResponseCookie.from", "Set-Cookie");
        // Raw CSRF/origin/referer enter only the richer port method, never a command record.
        assertThat(source).contains(
                "requestUseCase.request(command, csrfToken.trim(), clean(origin), clean(referer))",
                "cancelUseCase.cancel(command, csrfToken.trim(), clean(origin), clean(referer))");
    }

    @Test
    void noUsernameRecoveryHttpResponsesClassExists() {
        assertThat(Files.exists(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/usernamerecovery/"
                        + "UsernameRecoveryHttpResponses.java"))).isFalse();
        assertThat(Files.exists(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/"
                        + "UsernameRecoveryHttpResponses.java"))).isFalse();
    }

    @Test
    void controllerOnlyCatchesTypedApplicationExceptions() throws Exception {
        String source = Files.readString(Path.of(CONTROLLER));

        assertThat(source).contains(
                "onErrorResume(UsernameRecoveryStartException.class",
                "onErrorResume(UsernameRecoveryRequestException.class",
                "onErrorResume(UsernameRecoveryCancelException.class");
        assertThat(source).doesNotContain("onErrorResume(Exception.class",
                "onErrorResume(RuntimeException.class",
                "catch (Exception", "catch (RuntimeException");
    }

    @Test
    void controllerDefersTransportConversionAndScopesConversionHandlingNarrowly() throws Exception {
        String source = Files.readString(Path.of(CONTROLLER));

        assertThat(source).contains("Mono.defer");
        assertThat(source).doesNotContain(
                "onErrorResume(IllegalArgumentException.class",
                "onErrorResume(NullPointerException.class");
    }
}
