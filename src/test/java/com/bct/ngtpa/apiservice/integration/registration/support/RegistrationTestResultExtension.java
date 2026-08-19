package com.bct.ngtpa.apiservice.integration.registration.support;

import java.lang.reflect.Method;
import java.util.Optional;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

public final class RegistrationTestResultExtension implements TestWatcher, AfterAllCallback {

  private final RegistrationTestCatalog catalog;

  public RegistrationTestResultExtension() {
    try {
      this.catalog = RegistrationTestCatalog.loadDefault();
    } catch (Exception exception) {
      throw new IllegalStateException(
          "Unable to load registration integration catalogue", exception);
    }
  }

  @Override
  public void testSuccessful(ExtensionContext context) {
    record(context, RegistrationTestStatus.PASS, "Automated assertions passed.", "");
  }

  @Override
  public void testFailed(ExtensionContext context, Throwable cause) {
    record(context, RegistrationTestStatus.FAIL, "Automated assertions failed.", safeReason(cause));
  }

  @Override
  public void testAborted(ExtensionContext context, Throwable cause) {
    record(
        context, RegistrationTestStatus.BLOCKED, "Test execution was blocked.", safeReason(cause));
  }

  @Override
  public void testDisabled(ExtensionContext context, Optional<String> reason) {
    record(context, RegistrationTestStatus.SKIPPED, "Test was skipped.", reason.orElse(""));
  }

  @Override
  public void afterAll(ExtensionContext context) throws Exception {
    RegistrationTestResultStore.write(catalog);
  }

  private void record(
      ExtensionContext context, RegistrationTestStatus status, String actual, String reason) {
    Optional<Method> testMethod = context.getTestMethod();
    if (testMethod.isEmpty()) {
      return;
    }
    Method method = testMethod.get();
    RegistrationTestCase mapping = method.getAnnotation(RegistrationTestCase.class);
    if (mapping == null || mapping.value().length == 0) {
      // Harness self-tests and catalogue guards do not represent a catalogue
      // execution row. Subtask 006 performs the strict unmapped-ID audit.
      return;
    }
    String evidence =
        "target/surefire-reports/TEST-" + context.getRequiredTestClass().getName() + ".xml";
    for (String testId : mapping.value()) {
      catalog.require(testId);
      RegistrationTestResultStore.record(
          testId, new RegistrationTestResult(status, actual, reason, evidence));
    }
  }

  private String safeReason(Throwable cause) {
    if (cause == null) {
      return "";
    }
    String message = cause.getMessage();
    return cause.getClass().getSimpleName() + (message == null ? "" : ": " + message);
  }
}
