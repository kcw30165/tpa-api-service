package com.bct.ngtpa.apiservice.adapter.in.web.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

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
    assertThrows(
        UnsupportedOperationException.class, () -> descriptor.targets().add("notificationId"));
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
    assertThrows(UnsupportedOperationException.class, () -> descriptor.targets().add("x"));
  }

  @Test
  void rejectsNullHttpStatus() {
    assertThrows(
        NullPointerException.class,
        () ->
            new ErrorDescriptor(
                null,
                ApiStatus.SYSTEM_ERROR,
                "SYSTEM",
                "code",
                List.of(),
                "http.api.error",
                LogLevel.ERROR,
                true));
  }

  @Test
  void rejectsNullApiStatus() {
    assertThrows(
        NullPointerException.class,
        () ->
            new ErrorDescriptor(
                HttpStatus.INTERNAL_SERVER_ERROR,
                null,
                "SYSTEM",
                "code",
                List.of(),
                "http.api.error",
                LogLevel.ERROR,
                true));
  }

  @Test
  void rejectsNullErrorType() {
    assertThrows(
        NullPointerException.class,
        () ->
            new ErrorDescriptor(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ApiStatus.SYSTEM_ERROR,
                null,
                "code",
                List.of(),
                "http.api.error",
                LogLevel.ERROR,
                true));
  }

  @Test
  void rejectsNullErrorCode() {
    assertThrows(
        NullPointerException.class,
        () ->
            new ErrorDescriptor(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ApiStatus.SYSTEM_ERROR,
                "SYSTEM",
                null,
                List.of(),
                "http.api.error",
                LogLevel.ERROR,
                true));
  }

  @Test
  void rejectsNullLogEventName() {
    assertThrows(
        NullPointerException.class,
        () ->
            new ErrorDescriptor(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ApiStatus.SYSTEM_ERROR,
                "SYSTEM",
                "code",
                List.of(),
                null,
                LogLevel.ERROR,
                true));
  }

  @Test
  void rejectsNullLogLevel() {
    assertThrows(
        NullPointerException.class,
        () ->
            new ErrorDescriptor(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ApiStatus.SYSTEM_ERROR,
                "SYSTEM",
                "code",
                List.of(),
                "http.api.error",
                null,
                true));
  }
}
