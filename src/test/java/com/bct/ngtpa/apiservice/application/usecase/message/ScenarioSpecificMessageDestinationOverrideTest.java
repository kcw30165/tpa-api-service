package com.bct.ngtpa.apiservice.application.usecase.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.application.dto.message.*;
import com.bct.ngtpa.apiservice.application.port.out.MessageDeliveryPort;
import com.bct.ngtpa.apiservice.config.message.MessageDeliveryRuntimeConfig;
import com.bct.ngtpa.apiservice.infrastructure.environment.DeploymentEnvironmentPolicy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ScenarioSpecificMessageDestinationOverrideTest {
    @Test
        void nonProductionLoginAndForgotPasswordUseScenarioOverrideWhileRegistrationRemainsUnchangedAtGenericBoundary() {
        var properties = properties();
        var sms = new AtomicReference<String>();
        var email = new AtomicReference<String>();
        MessageDeliveryPort port = new MessageDeliveryRuntimeConfig().messageDeliveryPort(
                message -> {
                    sms.set(message.recipient());
                    return Mono.just(new SenderResult(SenderStatus.ACCEPTED, "sms", null, null));
                },
                message -> {
                    email.set(message.recipient());
                    return Mono.just(new SenderResult(SenderStatus.ACCEPTED, "email", null, null));
                },
                command -> new com.bct.ngtpa.apiservice.application.service.message.MessageTemplateRenderResult(
                        command.template()),
                new DeploymentEnvironmentPolicy(environment("DEV")), properties);
        StepVerifier
                .create(port
                        .deliver(command(MessageDeliveryScenario.LOGIN, MessageDeliveryChannel.EMAIL, "real-login")))
                .expectNextCount(1).verifyComplete();
        assertThat(email.get()).isEqualTo("login@example.test");
        StepVerifier
                .create(port.deliver(
                        command(MessageDeliveryScenario.FORGOT_PASSWORD, MessageDeliveryChannel.SMS, "real-forgot")))
                .expectNextCount(1).verifyComplete();
        assertThat(sms.get()).isEqualTo("forgot-mobile");
        StepVerifier
                .create(port.deliver(
                        command(MessageDeliveryScenario.REGISTRATION, MessageDeliveryChannel.SMS, "real-registration")))
                .expectNextCount(1).verifyComplete();
        assertThat(sms.get()).isEqualTo("real-registration");
    }

    @Test
    void productionLoginUsesOriginalRecipient() {
        var sms = new AtomicReference<String>();
        MessageDeliveryPort port = new MessageDeliveryRuntimeConfig().messageDeliveryPort(
                message -> {
                    sms.set(message.recipient());
                    return Mono.just(new SenderResult(SenderStatus.ACCEPTED, "sms", null, null));
                },
                message -> Mono.just(new SenderResult(SenderStatus.ACCEPTED, "email", null, null)),
                command -> new com.bct.ngtpa.apiservice.application.service.message.MessageTemplateRenderResult(
                        command.template()),
                new DeploymentEnvironmentPolicy(environment("PROD")), properties());
        StepVerifier
                .create(port.deliver(command(MessageDeliveryScenario.LOGIN, MessageDeliveryChannel.SMS, "real-login")))
                .expectNextCount(1).verifyComplete();
        assertThat(sms.get()).isEqualTo("real-login");
    }

    private MockEnvironment environment(String profile) {
        var e = new MockEnvironment();
        e.setActiveProfiles(profile);
        return e;
    }

    private AuthWorkflowProperties properties() {
        var p = new AuthWorkflowProperties();
        var r = new AuthWorkflowProperties.Registration();
        r.getMessage().getDestinationOverride().setMobile("reg-mobile");
        r.getMessage().getDestinationOverride().setEmail("reg@example.test");
        p.setRegistration(r);
        var l = new AuthWorkflowProperties.Login();
        l.getMessage().getDestinationOverride().setMobile("login-mobile");
        l.getMessage().getDestinationOverride().setEmail("login@example.test");
        p.setLogin(l);
                var f = new AuthWorkflowProperties.ForgotPassword();
                f.getMessage().getDestinationOverride().setMobile("forgot-mobile");
                f.getMessage().getDestinationOverride().setEmail("forgot@example.test");
                p.setForgotPassword(f);
        return p;
    }

    private MessageDeliveryCommand command(MessageDeliveryScenario scenario, MessageDeliveryChannel channel,
            String recipient) {
        return new MessageDeliveryCommand(MessageDeliveryType.LOGIN_OTP, scenario, channel, recipient,
                channel == MessageDeliveryChannel.EMAIL ? "subject" : null, "code {code}", null,
                Map.of("code", "123456"), "en", "req-1");
    }
}
