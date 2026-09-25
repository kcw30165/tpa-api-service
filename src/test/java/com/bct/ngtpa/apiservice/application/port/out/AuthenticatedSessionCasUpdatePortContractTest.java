package com.bct.ngtpa.apiservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Fail-first: confirms the generic port's shape -- ONE method, taking the command and returning the
 * generic result. Not scenario-specific, not CSRF-specific.
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

  @Test
  void doesNotExtendOrCoupleToTheCsrfSpecificPort() {
    // AssertJ's ClassAssert only exposes the POSITIVE isAssignableFrom(...); the negative
    // is expressed directly against the JDK's own Class#isAssignableFrom(Class) predicate.
    assertThat(
            AuthenticatedSessionCsrfRotationPort.class.isAssignableFrom(
                AuthenticatedSessionCasUpdatePort.class))
        .isFalse();
    assertThat(
            AuthenticatedSessionCasUpdatePort.class.isAssignableFrom(
                AuthenticatedSessionCsrfRotationPort.class))
        .isFalse();
  }
}
