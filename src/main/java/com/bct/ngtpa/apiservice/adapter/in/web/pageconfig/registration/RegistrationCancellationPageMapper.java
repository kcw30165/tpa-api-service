package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.registration;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.response.RegistrationCancellationPageActionResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.RegistrationCancellationPageResponse;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import java.util.List;
import java.util.Objects;

/**
 * Maps the registration cancellation result to FE-owned semantic identifiers.
 */
public final class RegistrationCancellationPageMapper {
    public static final String PAGE_KEY = "registration-cancelled";

    public RegistrationCancellationPageMapper(BffPagesProperties pages) {
        Objects.requireNonNull(pages, "pages must not be null");
    }

    public RegistrationCancellationPageResponse map(String ignoredLanguage) {
        return new RegistrationCancellationPageResponse(
                PAGE_KEY,
                List.of(new RegistrationCancellationPageActionResponse(
                        "returnToLogin",
                        "PRIMARY",
                        WorkflowNextStep.RETURN_TO_LOGIN)));
    }
}
