package com.bct.ngtpa.apiservice.application.service.message;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record MessageTemplateRenderCommand(String template, Map<String, String> templateVariables) {

    public MessageTemplateRenderCommand {
        templateVariables = templateVariables == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(templateVariables));
    }
}