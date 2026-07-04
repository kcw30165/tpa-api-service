package com.bct.ngtpa.apiservice.adapter.out.registration;

import com.bct.ngtpa.apiservice.application.dto.auth.OtpPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.RegistrationAuthWorkflowPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryChannel;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryCommand;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryScenario;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryStatus;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryType;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import com.bct.ngtpa.apiservice.application.port.out.MessageDeliveryPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.RegistrationMessageGateway;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

public class RegistrationMessageDeliveryGatewayAdapter implements RegistrationMessageGateway {
    private final MessageDeliveryPort messageDeliveryPort;
    private final AuthWorkflowPolicyPort authWorkflowPolicyPort;

    public RegistrationMessageDeliveryGatewayAdapter(
            MessageDeliveryPort messageDeliveryPort,
            AuthWorkflowPolicyPort authWorkflowPolicyPort) {
        this.messageDeliveryPort = Objects.requireNonNull(messageDeliveryPort, "messageDeliveryPort must not be null");
        this.authWorkflowPolicyPort = Objects.requireNonNull(authWorkflowPolicyPort, "authWorkflowPolicyPort must not be null");
    }

    @Override
    public Mono<Boolean> deliverRegistrationOtp(
            RegistrationContactType contactType,
            String recipient,
            String otpCode,
            int ttlSeconds,
            String acceptLanguage,
            String correlationId) {
        OtpPolicy policy = otpPolicy();
        MessageDeliveryChannel channel = toChannel(contactType);

        var variables = new LinkedHashMap<String, String>();
        variables.put("code", otpCode);
        variables.put("ttlValue", String.valueOf(Math.max(1, ttlSeconds / 60)));
        variables.put("ttlUnit", "minutes");

        var command = new MessageDeliveryCommand(
                MessageDeliveryType.REGISTRATION_OTP,
                MessageDeliveryScenario.REGISTRATION,
                channel,
                recipient,
                subject(channel, policy),
                body(channel, policy),
                "registration-otp",
                Map.copyOf(variables),
                acceptLanguage,
                correlationId);

        return messageDeliveryPort.deliver(command)
                .map(result -> result.status() == MessageDeliveryStatus.ACCEPTED);
    }

    private OtpPolicy otpPolicy() {
        RegistrationAuthWorkflowPolicy policy = authWorkflowPolicyPort.registrationPolicy();
        if (policy == null || policy.otp() == null) {
            throw new IllegalStateException("Registration OTP policy is not configured");
        }
        return policy.otp();
    }

    private MessageDeliveryChannel toChannel(RegistrationContactType contactType) {
        if (contactType == RegistrationContactType.MOBILE) {
            return MessageDeliveryChannel.SMS;
        }
        return MessageDeliveryChannel.EMAIL;
    }

    private String subject(MessageDeliveryChannel channel, OtpPolicy policy) {
        if (channel == MessageDeliveryChannel.EMAIL
                && policy.email() != null
                && policy.email().subject() != null
                && !policy.email().subject().isBlank()) {
            return policy.email().subject();
        }
        return "registration-otp";
    }

    private String body(MessageDeliveryChannel channel, OtpPolicy policy) {
        if (channel == MessageDeliveryChannel.EMAIL
                && policy.email() != null
                && policy.email().body() != null
                && !policy.email().body().isBlank()) {
            return policy.email().body();
        }
        if (policy.smsTemplate() != null && !policy.smsTemplate().isBlank()) {
            return policy.smsTemplate();
        }
        return "{code}";
    }
}