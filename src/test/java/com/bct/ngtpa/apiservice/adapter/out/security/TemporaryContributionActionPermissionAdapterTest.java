package com.bct.ngtpa.apiservice.adapter.out.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporaryContributionActionPermissionAdapterTest {

    @Test
    void resolveContributionActionsReturnsExportEnabled() {
        var adapter = new TemporaryContributionActionPermissionAdapter();
        var actions = adapter.resolveContributionActions();

        assertTrue(actions.exportEnabled());
    }
}
