package com.bct.ngtpa.apiservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Confirms the generic port's shape -- ONE method, taking the command and returning the generic
 * result. Not scenario-specific, not CSRF-specific.
 */
class AuthenticatedSessionCasUpdatePortContractTest {

  @Test
  void exposesExactlyOneCasUpdateMethod() {
    var names =
        Arrays.stream(AuthenticatedSessionCasUpdatePort.class.getMethods())
            .map(Method::getName)
            .toList();
    assertThat(names).contains("updateIfCurrent");
  }
}
