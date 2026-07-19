package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationSelectedTrustRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationUsernameCheckRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationUsernameCheckWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthRegistrationUsernameController;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.in.CheckRegistrationUsernameUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.CheckRegistrationUsernameCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.CheckRegistrationUsernameResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.CheckRegistrationUsernameResultStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import reactor.core.publisher.Mono;

class AuthRegistrationUsernameControllerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void usernameCheckEndpointExists() throws Exception {
        Method method = AuthRegistrationUsernameController.class.getDeclaredMethod(
                "checkUsername",
                RegistrationUsernameCheckRequest.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class);
        PostMapping mapping = method.getAnnotation(PostMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.path()).contains("/username/check");
    }

    @Test
    void missingAuthFlowCookieIsRejectedAndUseCaseNotCalled() throws Exception {
        var useCase = new RecordingUseCase(successResult());
        var controller = controller(useCase);

        JsonNode json = json(controller.checkUsername(validRequest(), null, "csrf", "req", "en", null, null).block());

        assertThat(json.get("success").asBoolean()).isFalse();
        assertThat(json.get("status").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(json.at("/errors/0/code").asText()).isEqualTo("err.registration.authFlow.invalid");
        assertThat(useCase.called).isFalse();
    }

    @Test
    void missingXsrfTokenIsRejectedAndUseCaseNotCalled() throws Exception {
        var useCase = new RecordingUseCase(successResult());
        var controller = controller(useCase);

        JsonNode json = json(controller.checkUsername(validRequest(), "afs", null, "req", "en", null, null).block());

        assertThat(json.get("success").asBoolean()).isFalse();
        assertThat(json.at("/errors/0/code").asText()).isEqualTo("err.registration.csrf.invalid");
        assertThat(useCase.called).isFalse();
    }

    @Test
    void missingBodyIsRejectedAndUseCaseNotCalled() throws Exception {
        var useCase = new RecordingUseCase(successResult());
        var controller = controller(useCase);

        JsonNode json = json(controller.checkUsername(null, "afs", "csrf", "req", "en", null, null).block());

        assertThat(json.get("success").asBoolean()).isFalse();
        assertThat(json.get("status").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(json.at("/errors/0/code").asText()).isEqualTo("err.registration.username.request.invalid");
        assertThat(useCase.called).isFalse();
    }

    @Test
    void requestMapsToCommandAndDoesNotRequireIdempotencyKey() {
        var useCase = new RecordingUseCase(successResult());
        var controller = controller(useCase);

        controller.checkUsername(validRequest(), "afs_abc", "csrf_abc", "req_abc", "en", "https://portal.example", "https://portal.example/start").block();

        assertThat(useCase.called).isTrue();
        assertThat(useCase.command.authFlowSessionId().value()).isEqualTo("afs_abc");
        assertThat(useCase.command.csrfToken()).isEqualTo("csrf_abc");
        assertThat(useCase.command.selectedTrust().trustCode()).isEqualTo("TL");
        assertThat(useCase.command.selectedTrust().accountEnv()).isEqualTo("ENV1");
        assertThat(useCase.command.username()).isEqualTo("abc1@");
        assertThat(useCase.command.acceptLanguage()).isEqualTo("en");
        assertThat(useCase.command.correlationId()).isEqualTo("req_abc");
        assertThat(useCase.command.origin()).isEqualTo("https://portal.example");
        assertThat(useCase.command.referer()).isEqualTo("https://portal.example/start");
        assertThat(commandComponents()).doesNotContain("idempotencyKey", "idempotencyTtl", "policyNo", "certNo", "password", "confirmPassword");
    }

    @Test
    void successResponseContract() throws Exception {
        var controller = controller(new RecordingUseCase(successResult()));

        JsonNode json = json(controller.checkUsername(validRequest(), "afs", "csrf", "req", "en", null, null).block());

        assertThat(json.get("success").asBoolean()).isTrue();
        assertThat(json.get("status").asText()).isEqualTo("SUCCESS");
        assertThat(json.at("/result/nextStep").asText()).isEqualTo("PASSWORD_SETUP");
        assertThat(json.get("messages")).isEmpty();
        assertThat(json.get("errors")).isEmpty();
        assertThat(json.toString()).doesNotContain("afs", "csrf", "abc1@");
    }

    @Test
    void validationFailureResponseContract() throws Exception {
        var result = new CheckRegistrationUsernameResult(
                CheckRegistrationUsernameResultStatus.VALIDATION_FAILED,
                WorkflowNextStep.USERNAME_CHECK,
                List.of(),
                List.of(error("err.registration.username.policyInvalid")));
        var controller = controller(new RecordingUseCase(result));

        JsonNode json = json(controller.checkUsername(validRequest(), "afs", "csrf", "req", "en", null, null).block());

        assertThat(json.get("success").asBoolean()).isFalse();
        assertThat(json.get("status").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(json.at("/result/nextStep").asText()).isEqualTo("USERNAME_CHECK");
        assertThat(json.at("/errors/0/code").asText()).isEqualTo("err.registration.username.policyInvalid");
        assertThat(json.at("/errors/0/target").asText()).isEqualTo("PAGE");
    }

    @Test
    void downstreamRejectionResponseContract() throws Exception {
        var result = new CheckRegistrationUsernameResult(
                CheckRegistrationUsernameResultStatus.DOWNSTREAM_REJECTED,
                WorkflowNextStep.USERNAME_CHECK,
                List.of(),
                List.of(error("err.registration.username.isRegistered")));
        var controller = controller(new RecordingUseCase(result));

        JsonNode json = json(controller.checkUsername(validRequest(), "afs", "csrf", "req", "en", null, null).block());

        assertThat(json.get("success").asBoolean()).isFalse();
        assertThat(json.get("status").asText()).isEqualTo("BUSINESS_REJECTED");
        assertThat(json.at("/result/nextStep").asText()).isEqualTo("USERNAME_CHECK");
        assertThat(json.at("/errors/0/code").asText()).isEqualTo("err.registration.username.isRegistered");
    }

    @Test
    void technicalDownstreamFailureResponseDoesNotUseBusinessFallback() throws Exception {
        var result = new CheckRegistrationUsernameResult(
                CheckRegistrationUsernameResultStatus.DOWNSTREAM_FAILED,
                WorkflowNextStep.USERNAME_CHECK,
                List.of(),
                List.of(error("err.workflow.downstream.unavailable")));
        var controller = controller(new RecordingUseCase(result));

        JsonNode json = json(controller.checkUsername(validRequest(), "afs", "csrf", "req", "en", null, null).block());

        assertThat(json.get("success").asBoolean()).isFalse();
        assertThat(json.get("status").asText()).isEqualTo("DOWNSTREAM_ERROR");
        assertThat(json.at("/result/nextStep").asText()).isEqualTo("USERNAME_CHECK");
        assertThat(json.toString()).doesNotContain("err.workflow.downstream.businessRejected", "err.registration.username.isRegistered");
    }

    @Test
    void extraIdempotencyHeaderCannotAffectControllerSignatureOrCommand() {
        assertThat(commandComponents()).doesNotContain("idempotencyKey", "RegistrationIdempotencyKey", "idempotencyTtl");
    }

    private JsonNode json(Object value) throws Exception {
        if (value instanceof org.springframework.http.ResponseEntity<?> response) {
            value = java.util.Objects.requireNonNull(response.getBody());
        }
        return objectMapper.valueToTree(value);
    }

    private static RegistrationUsernameCheckRequest validRequest() {
        return new RegistrationUsernameCheckRequest(new RegistrationSelectedTrustRequest("TL", "ENV1"), "abc1@");
    }

    private static WorkflowErrorDescriptor error(String code) {
        return new WorkflowErrorDescriptor(WorkflowErrorType.FORM, code, null, List.of("PAGE"), WorkflowErrorSeverity.ERROR, "SERVER");
    }

    private static CheckRegistrationUsernameResult successResult() {
        return new CheckRegistrationUsernameResult(CheckRegistrationUsernameResultStatus.USERNAME_AVAILABLE, WorkflowNextStep.PASSWORD_SETUP, List.of(), List.of());
    }

    private static List<String> commandComponents() {
        return Arrays.stream(CheckRegistrationUsernameCommand.class.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    private static AuthRegistrationUsernameController controller(CheckRegistrationUsernameUseCase useCase) {
        return new AuthRegistrationUsernameController(useCase, new RegistrationUsernameCheckWebMapper());
    }

    private static final class RecordingUseCase implements CheckRegistrationUsernameUseCase {
        final CheckRegistrationUsernameResult result;
        boolean called;
        CheckRegistrationUsernameCommand command;
        RecordingUseCase(CheckRegistrationUsernameResult result) { this.result = result; }
        @Override public Mono<CheckRegistrationUsernameResult> check(CheckRegistrationUsernameCommand command) {
            called = true;
            this.command = command;
            return Mono.just(result);
        }
    }
}
