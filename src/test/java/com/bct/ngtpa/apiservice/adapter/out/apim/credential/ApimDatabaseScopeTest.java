package com.bct.ngtpa.apiservice.adapter.out.apim.credential;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ApimDatabaseScopeTest {
    @Test
    void enumHasExactlyTwoValues() {
        ApimDatabaseScope[] values = ApimDatabaseScope.values();
        assertThat(values).hasSize(2);
        assertThat(values).containsExactly(ApimDatabaseScope.NON_ACCOUNT, ApimDatabaseScope.ACCOUNT_ENV);
    }
}
