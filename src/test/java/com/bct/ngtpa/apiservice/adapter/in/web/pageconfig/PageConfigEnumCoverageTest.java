package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PageConfigEnumCoverageTest {

  @Test
  void exposesWhenOperatorValues() {
    assertArrayEquals(
        new WhenOperator[] {
          WhenOperator.NOT_BLANK,
          WhenOperator.BLANK,
          WhenOperator.CHANGED,
          WhenOperator.ALL,
          WhenOperator.ANY,
          WhenOperator.ALL_GROUPS_EMPTY
        },
        WhenOperator.values());
    assertEquals(WhenOperator.CHANGED, WhenOperator.valueOf("CHANGED"));
  }

  @Test
  void exposesThenOperatorValues() {
    assertArrayEquals(
        new ThenOperator[] {
          ThenOperator.FAIL,
          ThenOperator.REQUIRED,
          ThenOperator.ALL_REQUIRED,
          ThenOperator.SHOW_MESSAGE
        },
        ThenOperator.values());
    assertEquals(ThenOperator.SHOW_MESSAGE, ThenOperator.valueOf("SHOW_MESSAGE"));
  }

  @Test
  void exposesSeverityValues() {
    assertArrayEquals(
        new Severity[] {Severity.ERROR, Severity.WARNING, Severity.INFO}, Severity.values());
    assertEquals(Severity.WARNING, Severity.valueOf("WARNING"));
  }
}
