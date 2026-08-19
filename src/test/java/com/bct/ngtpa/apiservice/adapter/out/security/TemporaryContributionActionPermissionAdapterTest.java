package com.bct.ngtpa.apiservice.adapter.out.security;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TemporaryContributionActionPermissionAdapterTest {

  @Test
  void resolveContributionActionsReturnsExportEnabled() {
    var adapter = new TemporaryContributionActionPermissionAdapter();
    var actions = adapter.resolveContributionActions();

    assertTrue(actions.exportEnabled());
  }
}
