package com.bct.ngtpa.apiservice.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;

/**
 * Closes the remaining reachable branch-coverage gaps in the private InvocationContext record,
 * identified by the script 007 jacoco.xml investigation: the fromJoinPoint factory (used only by
 * the use-case-package pointcut) and the equals() branches. Exercised directly via reflection,
 * mirroring the approach used for the toString() coverage test (script 003), so no real AOP weaving
 * through a use-case Service class is required.
 */
class ExecutionLoggingAspectInvocationContextEqualsAndFactoryTest {

  private static Class<?> invocationContextClass() throws ClassNotFoundException {
    // Avoid S1872 (classes should not be compared by name): InvocationContext is a
    // private member class that cannot be referenced via a class literal from this
    // test, so its Class<?> is constructed directly from the binary name instead of
    // scanning getDeclaredClasses() with a getSimpleName().equals(...) check.
    return Class.forName(ExecutionLoggingAspect.class.getName() + "$InvocationContext");
  }

  private static Object newContext(
      String className,
      String methodName,
      String label,
      boolean logArgs,
      boolean logResult,
      Object[] args)
      throws Exception {
    Class<?> cls = invocationContextClass();
    Constructor<?> constructor =
        cls.getDeclaredConstructor(
            String.class, String.class, String.class, boolean.class, boolean.class, Object[].class);
    constructor.setAccessible(true);
    return constructor.newInstance(className, methodName, label, logArgs, logResult, args);
  }

  private static class TestService {}

  @Test
  void fromJoinPointBuildsContextUsingClassNameDotMethodNameAsLabel() throws Exception {
    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    MethodSignature signature = mock(MethodSignature.class);
    when(joinPoint.getSignature()).thenReturn(signature);
    doReturn(TestService.class).when(signature).getDeclaringType();
    when(signature.getName()).thenReturn("execute");
    Object[] args = new Object[] {"a", 1};
    when(joinPoint.getArgs()).thenReturn(args);

    Class<?> cls = invocationContextClass();
    Method fromJoinPoint = cls.getDeclaredMethod("fromJoinPoint", ProceedingJoinPoint.class);
    fromJoinPoint.setAccessible(true);
    Object context = fromJoinPoint.invoke(null, joinPoint);

    Method classNameMethod = cls.getDeclaredMethod("className");
    Method methodNameMethod = cls.getDeclaredMethod("methodName");
    Method labelMethod = cls.getDeclaredMethod("label");
    Method logArgsMethod = cls.getDeclaredMethod("logArgs");
    Method logResultMethod = cls.getDeclaredMethod("logResult");
    Method argsMethod = cls.getDeclaredMethod("args");

    assertThat(classNameMethod.invoke(context)).isEqualTo("TestService");
    assertThat(methodNameMethod.invoke(context)).isEqualTo("execute");
    assertThat(labelMethod.invoke(context)).isEqualTo("TestService.execute");
    assertThat(logArgsMethod.invoke(context)).isEqualTo(true);
    assertThat(logResultMethod.invoke(context)).isEqualTo(false);
    assertThat((Object[]) argsMethod.invoke(context)).isEqualTo(args);
  }

  @Test
  void equalsReturnsTrueForSameInstance() throws Exception {
    Object context = newContext("C", "m", "lbl", true, false, new Object[] {"x"});
    assertThat(context.equals(context)).isTrue();
  }

  @Test
  void equalsReturnsFalseForNull() throws Exception {
    Object context = newContext("C", "m", "lbl", true, false, new Object[] {"x"});
    assertThat(context.equals(null)).isFalse();
  }

  @Test
  void equalsReturnsFalseForDifferentClass() throws Exception {
    Object context = newContext("C", "m", "lbl", true, false, new Object[] {"x"});
    assertThat(context.equals("not a context")).isFalse();
  }

  @Test
  void equalsReturnsFalseWhenFieldsDiffer() throws Exception {
    Object a = newContext("C", "m", "lbl", true, false, new Object[] {"x"});
    Object b = newContext("C", "m", "different-label", true, false, new Object[] {"x"});
    assertThat(a.equals(b)).isFalse();
  }

  @Test
  void equalsReturnsTrueWhenAllFieldsMatchIncludingArgsContent() throws Exception {
    Object a = newContext("C", "m", "lbl", true, false, new Object[] {"x", 1});
    Object b = newContext("C", "m", "lbl", true, false, new Object[] {"x", 1});
    assertThat(a.equals(b)).isTrue();
  }

  @Test
  void equalsReturnsFalseWhenClassNameDiffers() throws Exception {
    Object a = newContext("C1", "m", "lbl", true, false, new Object[] {"x"});
    Object b = newContext("C2", "m", "lbl", true, false, new Object[] {"x"});
    assertThat(a.equals(b)).isFalse();
  }

  @Test
  void equalsReturnsFalseWhenLogArgsDiffers() throws Exception {
    Object a = newContext("C", "m", "lbl", true, false, new Object[] {"x"});
    Object b = newContext("C", "m", "lbl", false, false, new Object[] {"x"});
    assertThat(a.equals(b)).isFalse();
  }

  @Test
  void equalsReturnsFalseWhenLogResultDiffers() throws Exception {
    Object a = newContext("C", "m", "lbl", true, false, new Object[] {"x"});
    Object b = newContext("C", "m", "lbl", true, true, new Object[] {"x"});
    assertThat(a.equals(b)).isFalse();
  }
}
