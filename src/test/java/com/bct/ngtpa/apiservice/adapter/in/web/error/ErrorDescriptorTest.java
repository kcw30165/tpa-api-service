package com.bct.ngtpa.apiservice.adapter.in.web.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Sub-task 1.7 (M1 closure, Option A) rewrite: {@link ErrorDescriptor} no longer carries a single
 * {@code errorType}/{@code errorCode}/{@code targets} triple; it carries a {@code
 * List<ErrorEntry>}, so every assertion that used to read {@code descriptor.errorType()} etc. now
 * reads {@code descriptor.errors().getFirst().type()} etc. {@link #single}/{@link
 * #singleWithoutMessage} are also covered here since they are the convenience factories every
 * pre-existing single-error mapping now uses.
 */
class ErrorDescriptorTest {

  @Test
  void singleBuildsOneResolveEntryAndRetainsEveryOtherField() {
    var descriptor =
        ErrorDescriptor.single(
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
    assertEquals(1, descriptor.errors().size());
    ErrorEntry entry = descriptor.errors().getFirst();
    assertEquals("FORM", entry.type());
    assertEquals("err.request.body.malformed", entry.code());
    assertTrue(entry.targets().isEmpty());
    assertEquals(MessagePolicy.RESOLVE, entry.policy());
    assertEquals("http.api.error", descriptor.logEventName());
    assertEquals(LogLevel.WARN, descriptor.logLevel());
    assertEquals(false, descriptor.includeStackTrace());
    assertTrue(descriptor.extraLogFields().isEmpty());
  }

  @Test
  void singleWithoutMessageBuildsOneNoneEntry() {
    var descriptor =
        ErrorDescriptor.singleWithoutMessage(
            HttpStatus.SERVICE_UNAVAILABLE,
            ApiStatus.SYSTEM_ERROR,
            "SYSTEM",
            "err.fund-fact-sheet.retrieval.unavailable",
            List.of(),
            "http.api.error",
            LogLevel.ERROR,
            false);

    ErrorEntry entry = descriptor.errors().getFirst();
    assertEquals(MessagePolicy.NONE, entry.policy());
    assertTrue(descriptor.extraLogFields().isEmpty());
  }

  @Test
  void singleWithoutMessageOverloadCarriesExtraLogFields() {
    Map<String, Object> extraFields = Map.of("collection", "fundDtl", "rowIndex", 3);

    var descriptor =
        ErrorDescriptor.singleWithoutMessage(
            HttpStatus.SERVICE_UNAVAILABLE,
            ApiStatus.SYSTEM_ERROR,
            "SYSTEM",
            "err.system.unavailable",
            List.of(),
            "http.api.error",
            LogLevel.ERROR,
            false,
            extraFields);

    Map<String, Object> actualExtraLogFields = descriptor.extraLogFields();
    assertEquals(extraFields, actualExtraLogFields);
    assertThrows(UnsupportedOperationException.class, () -> actualExtraLogFields.put("x", "y"));
  }

  @Test
  void nullErrorsListBecomesEmptyImmutableList() {
    var descriptor =
        new ErrorDescriptor(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ApiStatus.SYSTEM_ERROR,
            null,
            "http.api.error",
            LogLevel.ERROR,
            true,
            null);

    assertTrue(descriptor.errors().isEmpty());
    assertTrue(descriptor.extraLogFields().isEmpty());
    List<ErrorEntry> errors = descriptor.errors();
    assertThrows(UnsupportedOperationException.class, () -> errors.add(null));
  }

  @Test
  void providedErrorsListIsDefensivelyCopiedAndImmutable() {
    List<ErrorEntry> mutableErrors =
        new ArrayList<>(
            List.of(
                new ErrorEntry(
                    "FIELD",
                    "err.request.validation.failed",
                    List.of("a"),
                    MessagePolicy.RESOLVE)));

    var descriptor =
        new ErrorDescriptor(
            HttpStatus.BAD_REQUEST,
            ApiStatus.VALIDATION_FAILED,
            mutableErrors,
            "http.api.error",
            LogLevel.WARN,
            false,
            Map.of());

    mutableErrors.add(
        new ErrorEntry(
            "FIELD", "err.request.validation.failed", List.of("b"), MessagePolicy.RESOLVE));

    assertEquals(1, descriptor.errors().size());
    List<ErrorEntry> errors = descriptor.errors();
    assertThrows(UnsupportedOperationException.class, () -> errors.add(null));
  }

  @Test
  void rejectsNullHttpStatus() {
    List<ErrorEntry> errors = List.of();
    Map<String, Object> extraLogFields = Map.of();
    assertThrows(
        NullPointerException.class,
        () ->
            new ErrorDescriptor(
                null,
                ApiStatus.SYSTEM_ERROR,
                errors,
                "http.api.error",
                LogLevel.ERROR,
                true,
                extraLogFields));
  }

  @Test
  void rejectsNullApiStatus() {
    List<ErrorEntry> errors = List.of();
    Map<String, Object> extraLogFields = Map.of();
    assertThrows(
        NullPointerException.class,
        () ->
            new ErrorDescriptor(
                HttpStatus.INTERNAL_SERVER_ERROR,
                null,
                errors,
                "http.api.error",
                LogLevel.ERROR,
                true,
                extraLogFields));
  }

  @Test
  void rejectsNullLogEventName() {
    List<ErrorEntry> errors = List.of();
    Map<String, Object> extraLogFields = Map.of();
    assertThrows(
        NullPointerException.class,
        () ->
            new ErrorDescriptor(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ApiStatus.SYSTEM_ERROR,
                errors,
                null,
                LogLevel.ERROR,
                true,
                extraLogFields));
  }

  @Test
  void rejectsNullLogLevel() {
    List<ErrorEntry> errors = List.of();
    Map<String, Object> extraLogFields = Map.of();
    assertThrows(
        NullPointerException.class,
        () ->
            new ErrorDescriptor(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ApiStatus.SYSTEM_ERROR,
                errors,
                "http.api.error",
                null,
                true,
                extraLogFields));
  }

  @Test
  void errorEntryRejectsNullType() {
    List<String> targets = List.of();
    assertThrows(
        NullPointerException.class,
        () -> new ErrorEntry(null, "code", targets, MessagePolicy.RESOLVE));
  }

  @Test
  void errorEntryRejectsNullCode() {
    List<String> targets = List.of();
    assertThrows(
        NullPointerException.class,
        () -> new ErrorEntry("FORM", null, targets, MessagePolicy.RESOLVE));
  }

  @Test
  void errorEntryRejectsNullPolicy() {
    List<String> targets = List.of();
    assertThrows(NullPointerException.class, () -> new ErrorEntry("FORM", "code", targets, null));
  }

  @Test
  void errorEntryNullTargetsBecomesEmptyImmutableList() {
    var entry = new ErrorEntry("FORM", "code", null, MessagePolicy.RESOLVE);

    assertTrue(entry.targets().isEmpty());
  }
}
