package com.bct.ngtpa.apiservice.adapter.out.apim.credential;

import org.junit.jupiter.api.Test;
import java.lang.reflect.RecordComponent;
import static org.assertj.core.api.Assertions.assertThat;

class ApimCredentialResolutionContextTest {
    @Test
    void contextHasExactlyTwoComponents() {
        RecordComponent[] comps = ApimCredentialResolutionContext.class.getRecordComponents();
        assertThat(comps).hasSize(2);
        assertThat(comps[0].getName()).isEqualTo("actorUserRole");
        assertThat(comps[1].getName()).isEqualTo("accountEnv");
    }
}
