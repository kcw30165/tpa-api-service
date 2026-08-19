package com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupAccount;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import java.util.List;

public record CompleteRegistrationResult(
    CompleteRegistrationResultStatus status,
    WorkflowNextStep nextStep,
    boolean registered,
    boolean clearAuthFlowCookie,
    List<RegistrationLookupAccount> remainingTrusts,
    List<WorkflowErrorDescriptor> errors) {
  public CompleteRegistrationResult {
    remainingTrusts = remainingTrusts == null ? List.of() : List.copyOf(remainingTrusts);
    errors = errors == null ? List.of() : List.copyOf(errors);
  }
}
