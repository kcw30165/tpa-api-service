package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.identity;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ForgotPasswordIdentitySubtask004FailFirstTest {
  @Test
  void identityCapabilityAndGenericMutationLockExist() throws Exception {
    assertDoesNotThrow(
        () ->
            Class.forName(
                "com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.identity.ForgotPasswordIdentityVerifier"));
    assertDoesNotThrow(
        () ->
            Class.forName(
                "com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionMutationLockPort"));
    assertDoesNotThrow(
        () ->
            Class.forName(
                "com.bct.ngtpa.apiservice.adapter.out.redis.auth.RedisAuthFlowSessionMutationLockAdapter"));
  }

  static Stream<Arguments> mappings() {
    return Stream.of(
        Arguments.of("GPN", "GPN"), Arguments.of("GPFS", "GPFS"),
        Arguments.of("STAFF_NO", "STAFFNO"), Arguments.of("ID", "ID"));
  }

  @ParameterizedTest
  @MethodSource("mappings")
  void mappingsAreExplicitAndExact(String submitted, String expected) throws Exception {
    Object verifier = verifier();
    Method method = verifier.getClass().getMethod("mapPublicIdentityType", String.class);
    assertEquals(expected, method.invoke(verifier, submitted).toString());
  }

  @Test
  void unknownBlankNullAndCaseVariantsAreRejected() throws Exception {
    Object verifier = verifier();
    Method method = verifier.getClass().getMethod("mapPublicIdentityType", String.class);
    for (String value : new String[] {"", " ", "staff", "gpn", "UNKNOWN", " STAFF_NO "})
      assertNull(method.invoke(verifier, value));
    assertNull(method.invoke(verifier, new Object[] {null}));
  }

  @Test
  void valueComparisonIsTrimOnlyAndPreservesMeaningfulCharacters() throws Exception {
    Object verifier = verifier();
    Method method =
        verifier
            .getClass()
            .getMethod("matches", String.class, String.class, String.class, String.class);
    assertEquals(true, method.invoke(verifier, "STAFF_NO", " 3010008 ", "STAFFNO", "3010008"));
    assertEquals(false, method.invoke(verifier, "ID", "Ab- 01", "ID", "ab- 01"));
    assertEquals(false, method.invoke(verifier, "ID", "001", "ID", "1"));
    assertEquals(false, method.invoke(verifier, "ID", "A  B", "ID", "A B"));
    assertEquals(false, method.invoke(verifier, "GPN", "3010008", "GPFS", "3010008"));
  }

  @Test
  void schemeCodeIsNotPartOfTheVerifierContract() throws Exception {
    for (Method method :
        Class.forName(
                "com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.identity.ForgotPasswordIdentityVerifier")
            .getDeclaredMethods()) assertFalse(method.toString().toLowerCase().contains("scheme"));
  }

  private Object verifier() throws Exception {
    return Class.forName(
            "com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.identity.ForgotPasswordIdentityVerifier")
        .getConstructor()
        .newInstance();
  }
}
