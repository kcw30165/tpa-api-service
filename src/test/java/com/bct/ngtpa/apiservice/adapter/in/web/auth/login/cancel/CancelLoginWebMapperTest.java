package com.bct.ngtpa.apiservice.adapter.in.web.auth.login.cancel;

import static org.assertj.core.api.Assertions.assertThat;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.cancel.*;
import org.junit.jupiter.api.Test;

class CancelLoginWebMapperTest {
    private final CancelLoginWebMapper mapper = new CancelLoginWebMapper();

    @Test
    void cleanupReturnsToLogin() {
        var r = mapper.toMutationResponse(
                new CancelLoginResult(CancelLoginStatus.CANCELLED, true, true, java.util.List.of()));
        assertThat(r.success()).isTrue();
        assertThat(r.result().nextStep()).isEqualTo(WorkflowNextStep.RETURN_TO_LOGIN);
    }
}
