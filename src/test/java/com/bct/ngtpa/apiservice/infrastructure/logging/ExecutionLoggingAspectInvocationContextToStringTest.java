package com.bct.ngtpa.apiservice.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Covers java:S6218 — InvocationContext (a private record with an {@code Object[] args} field) must
 * not rely on the auto-generated {@code toString()}, which prints the array's identity hash instead
 * of its content.
 */
class ExecutionLoggingAspectInvocationContextToStringTest {

  @Test
  void toStringRendersArrayContentsInsteadOfDefaultIdentity() throws Exception {
    Class<?> invocationContextClass =
        Arrays.stream(ExecutionLoggingAspect.class.getDeclaredClasses())
            .filter(c -> c.getSimpleName().equals("InvocationContext"))
            .findFirst()
            .orElseThrow();

    Constructor<?> constructor =
        invocationContextClass.getDeclaredConstructor(
            String.class, String.class, String.class, boolean.class, boolean.class, Object[].class);
    constructor.setAccessible(true);

    Object context =
        constructor.newInstance(
            "SomeClass", "someMethod", "label", true, true, new Object[] {"alpha", 42});

    String rendered = context.toString();

    assertThat(rendered).doesNotContain("[Ljava.lang.Object;@");
    assertThat(rendered).contains("alpha");
    assertThat(rendered).contains("42");
  }
}
