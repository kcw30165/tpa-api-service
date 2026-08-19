package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.registration;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.response.RegistrationSuccessPageActionResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.RegistrationSuccessPageResponse;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationWorkflowConstants;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Maps registration completion to FE-owned page/action identifiers only. */
@Component
public class RegistrationSuccessPageMapper {

  public RegistrationSuccessPageMapper(BffPagesProperties pages) {
    Objects.requireNonNull(pages, "pages must not be null");
  }

  public RegistrationSuccessPageResponse map(
      String pageId, String variant, String ignoredLanguage) {
    return new RegistrationSuccessPageResponse(pageId, variant, actions(variant));
  }

  private List<RegistrationSuccessPageActionResponse> actions(String variant) {
    if (RegistrationWorkflowConstants.SUCCESS_VARIANT_HAS_REMAINING_TRUSTS.equals(variant)) {
      return List.of(
          new RegistrationSuccessPageActionResponse(
              "registerAnotherTrust", "PRIMARY", WorkflowNextStep.SELECT_TRUST),
          new RegistrationSuccessPageActionResponse(
              "returnToLogin", "SECONDARY", WorkflowNextStep.RETURN_TO_LOGIN));
    }
    return List.of(
        new RegistrationSuccessPageActionResponse(
            "returnToLogin", "PRIMARY", WorkflowNextStep.RETURN_TO_LOGIN));
  }
}
