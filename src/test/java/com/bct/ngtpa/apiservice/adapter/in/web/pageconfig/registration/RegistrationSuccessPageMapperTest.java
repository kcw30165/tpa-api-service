package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationWorkflowConstants;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import org.junit.jupiter.api.Test;

class RegistrationSuccessPageMapperTest {

    private final RegistrationSuccessPageMapper mapper =
            new RegistrationSuccessPageMapper(new BffPagesProperties());

    @Test
    void noRemainingTrustsReturnsLanguageIndependentLoginAction() {
        var english = mapper.map(
                RegistrationWorkflowConstants.PAGE_REGISTRATION_SUCCESS,
                RegistrationWorkflowConstants.SUCCESS_VARIANT_NO_REMAINING_TRUSTS,
                "en");
        var chinese = mapper.map(
                RegistrationWorkflowConstants.PAGE_REGISTRATION_SUCCESS,
                RegistrationWorkflowConstants.SUCCESS_VARIANT_NO_REMAINING_TRUSTS,
                "zh-HK");

        assertThat(english).isEqualTo(chinese);
        assertThat(english.pageId())
                .isEqualTo(RegistrationWorkflowConstants.PAGE_REGISTRATION_SUCCESS);
        assertThat(english.variant())
                .isEqualTo(RegistrationWorkflowConstants.SUCCESS_VARIANT_NO_REMAINING_TRUSTS);
        assertThat(english.actions()).singleElement().satisfies(action -> {
            assertThat(action.id()).isEqualTo("returnToLogin");
            assertThat(action.type()).isEqualTo("PRIMARY");
            assertThat(action.nextStep()).isEqualTo(WorkflowNextStep.RETURN_TO_LOGIN);
        });
    }

    @Test
    void remainingTrustsReturnsRegisterAnotherTrustAndLoginActions() {
        var response = mapper.map(
                RegistrationWorkflowConstants.PAGE_REGISTRATION_SUCCESS,
                RegistrationWorkflowConstants.SUCCESS_VARIANT_HAS_REMAINING_TRUSTS,
                "en");

        assertThat(response.variant())
                .isEqualTo(RegistrationWorkflowConstants.SUCCESS_VARIANT_HAS_REMAINING_TRUSTS);
        assertThat(response.actions())
                .extracting(action -> action.id())
                .containsExactly("registerAnotherTrust", "returnToLogin");
        assertThat(response.actions())
                .extracting(action -> action.nextStep())
                .containsExactly(WorkflowNextStep.SELECT_TRUST, WorkflowNextStep.RETURN_TO_LOGIN);
    }
}
