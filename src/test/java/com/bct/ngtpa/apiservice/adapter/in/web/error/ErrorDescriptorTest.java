package com.bct.ngtpa.apiservice.adapter.in.web.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * SonarQube fix (post-M1 quality gate, java:S5778): every {@code assertThrows} lambda that
 * previously contained two or more method invocations that could each throw (for example a {@code
 * List.of()} argument expression evaluated alongside the constructor call under test) now has every
 * "safe" invocation extracted into a local variable before the {@code assertThrows} call, leaving
 * only the single invocation actually expected to throw inside the lambda. No assertions or test
 * intent changed.
 */
class ErrorDescriptorTest {

  @Test
  void retainsEveryFieldOfTheSubtask10MappingTableRow() {
    var descriptor =
        new ErrorDescriptor(
            HttpStatus.BAD_REQUEST,
            ApiStatus.VALIDATION_FAILED,
            "FORM",
            "err.request.body.malformed",
            List.of(),
            "http.api.error",
            LogLevel.WARN,
            false);

    assertEquals(HttpStatus.BAD_REQUEST, descriptor.httpStatus());
    assertEquals(ApiStatus.VALIDATION_FAILED, descriptor.apiStatus());
    assertEquals("FORM", descriptor.errorType());
    assertEquals("err.request.body.malformed", descriptor.errorCode());
    assertTrue(descriptor.targets().isEmpty());
    assertEquals("http.api.error", descriptor.logEventName());
    assertEquals(LogLevel.WARN, descriptor.logLevel());
    assertEquals(false, descriptor.includeStackTrace());
  }

  @Test
  void nullTargetsListBecomesEmptyImmutableList() {
    var descriptor =
        new ErrorDescriptor(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ApiStatus.SYSTEM_ERROR,
            "SYSTEM",
            "SYSTEM_UNEXPECTED",
            null,
            "http.api.error",
            LogLevel.ERROR,
            true);

    assertTrue(descriptor.targets().isEmpty());
    List<String> targets = descriptor.targets();
    assertThrows(UnsupportedOperationException.class, () -> targets.add("notificationId"));
  }

  @Test
  void providedTargetsListIsDefensivelyCopiedAndImmutable() {
    List<String> mutableTargets = new ArrayList<>(List.of("notificationId"));
    var descriptor =
        new ErrorDescriptor(
            HttpStatus.BAD_REQUEST,
            ApiStatus.VALIDATION_FAILED,
            "FIELD",
            "err.request.validation.failed",
            mutableTargets,
            "http.api.error",
            LogLevel.WARN,
            false);

    mutableTargets.add("another");

    assertEquals(List.of("notificationId"), descriptor.targets());
    List<String> targets = descriptor.targets();
    assertThrows(UnsupportedOperationException.class, () -> targets.add("x"));
  }

  @Test
  void rejectsNullHttpStatus() {
    List<String> targets = List.of();
    assertThrows(
        NullPointerException.class,
        () ->
            new ErrorDescriptor(
                null,
                ApiStatus.SYSTEM_ERROR,
                "SYSTEM",
                "code",
                targets,
                "http.api.error",
                LogLevel.ERROR,
                true));
  }

  @Test
  void rejectsNullApiStatus() {
    List<String> targets = List.of();
    assertThrows(
        NullPointerException.class,
        () ->
            new ErrorDescriptor(
                HttpStatus.INTERNAL_SERVER_ERROR,
                null,
                "SYSTEM",
                "code",
                targets,
                "http.api.error",
                LogLevel.ERROR,
                true));
  }

  @Test
  void rejectsNullErrorType() {
    List<String> targets = List.of();
    assertThrows(
        NullPointerException.class,
        () ->
            new ErrorDescriptor(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ApiStatus.SYSTEM_ERROR,
                null,
                "code",
                targets,
                "http.api.error",
                LogLevel.ERROR,
                true));
  }

  @Test
  void rejectsNullErrorCode() {
    List<String> targets = List.of();
    assertThrows(
        NullPointerException.class,
        () ->
            new ErrorDescriptor(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ApiStatus.SYSTEM_ERROR,
                "SYSTEM",
                null,
                targets,
                "http.api.error",
                LogLevel.ERROR,
                true));
  }

  @Test
  void rejectsNullLogEventName() {
    List<String> targets = List.of();
    assertThrows(
        NullPointerException.class,
        () ->
            new ErrorDescriptor(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ApiStatus.SYSTEM_ERROR,
                "SYSTEM",
                "code",
                targets,
                null,
                LogLevel.ERROR,
                true));
  }

  @Test
  void rejectsNullLogLevel() {
    List<String> targets = List.of();
    assertThrows(
        NullPointerException.class,
        () ->
            new ErrorDescriptor(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ApiStatus.SYSTEM_ERROR,
                "SYSTEM",
                "code",
                targets,
                "http.api.error",
                null,
                true));
  }
}
