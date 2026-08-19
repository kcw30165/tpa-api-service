package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.util.StringUtils;

public final class WorkflowErrorContractValidator {
  private WorkflowErrorContractValidator() {}

  public static void validate(WorkflowErrorContractProperties properties) {
    if (properties == null) {
      throw new IllegalStateException("Workflow error contract configuration is missing");
    }

    validateBoundDefinitions(properties);
    var definitions = properties.definitionsByCode();

    Set<String> required = new LinkedHashSet<>();
    for (var key : RegistrationErrorKey.values()) {
      required.add(key.value());
    }
    for (var key : SharedWorkflowErrorKey.values()) {
      required.add(key.value());
    }

    for (String code : required) {
      var definition = definitions.get(code);
      if (definition == null) {
        throw new IllegalStateException("Workflow error definition is missing: " + code);
      }
      validateDefinition(code, definition, properties);
    }
  }

  private static void validateBoundDefinitions(WorkflowErrorContractProperties properties) {
    if (properties.getErrors() == null || properties.getErrors().isEmpty()) {
      throw new IllegalStateException("Workflow error definitions are missing");
    }
    for (var definition : properties.getErrors()) {
      if (definition == null || !StringUtils.hasText(definition.getCode())) {
        throw new IllegalStateException("Workflow error definition code is missing");
      }
      String code = definition.getCode().trim();
      if (!code.startsWith("err.")) {
        throw new IllegalStateException("Workflow error code must start with err.: " + code);
      }
      validateDefinition(code, definition, properties);
    }
    // Also performs duplicate-code detection.
    properties.definitionsByCode();
  }

  private static void validateDefinition(
      String code,
      WorkflowErrorContractProperties.Definition definition,
      WorkflowErrorContractProperties properties) {
    if (definition.getType() == null
        || definition.getSeverity() == null
        || !StringUtils.hasText(definition.getSource())) {
      throw new IllegalStateException("Workflow error metadata is incomplete: " + code);
    }
    if (definition.getTargets() == null
        || definition.getTargets().isEmpty()
        || definition.getTargets().stream().anyMatch(value -> !StringUtils.hasText(value))) {
      throw new IllegalStateException("Workflow error targets are missing: " + code);
    }
    if (definition.getNextStep() == null) {
      throw new IllegalStateException("Workflow error nextStep is missing: " + code);
    }
    if (definition.getNextStep() != WorkflowNextStep.NONE) {
      var navigation = properties.getNavigation().get(definition.getNextStep());
      if (navigation == null || !StringUtils.hasText(navigation.getPageId())) {
        throw new IllegalStateException(
            "Workflow navigation is missing: " + definition.getNextStep());
      }
    }
  }
}
