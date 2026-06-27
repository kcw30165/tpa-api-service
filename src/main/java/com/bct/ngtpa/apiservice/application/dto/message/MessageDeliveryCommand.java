package com.bct.ngtpa.apiservice.application.dto.message;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record MessageDeliveryCommand(
        MessageDeliveryType messageType,
        MessageDeliveryScenario scenario,
        MessageDeliveryChannel channel,
        String recipient,
        String subject,
        String body,
        String templateCode,
        Map<String, String> templateVariables,
        String locale,
        String correlationId) {

    public MessageDeliveryCommand {
        templateVariables = templateVariables == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(templateVariables));
    }
}