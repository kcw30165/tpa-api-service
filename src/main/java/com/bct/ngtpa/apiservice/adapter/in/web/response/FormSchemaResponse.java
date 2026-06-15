package com.bct.ngtpa.apiservice.adapter.in.web.response;

import java.util.List;
import java.util.Map;

public record FormSchemaResponse(
        String id,
        String version,
        String mode,
        List<SectionResponse> sections,
        Map<String, List<FormOptionResponse>> optionSets,
        List<ValidationRuleResponse> validationRules,
        Map<String, Object> confirmation,
        Map<String, Object> actions) {

    public FormSchemaResponse {
        sections = sections == null ? List.of() : List.copyOf(sections);
        optionSets = optionSets == null ? Map.of() : Map.copyOf(optionSets);
        validationRules = validationRules == null ? List.of() : List.copyOf(validationRules);
        confirmation = confirmation == null ? Map.of() : Map.copyOf(confirmation);
        actions = actions == null ? Map.of() : Map.copyOf(actions);
    }

    public FormSchemaResponse(
            String id,
            String version,
            String mode,
            List<SectionResponse> sections,
            List<ValidationRuleResponse> validationRules,
            Map<String, Object> confirmation,
            Map<String, Object> actions) {
        this(id, version, mode, sections, Map.of(), validationRules, confirmation, actions);
    }
}
