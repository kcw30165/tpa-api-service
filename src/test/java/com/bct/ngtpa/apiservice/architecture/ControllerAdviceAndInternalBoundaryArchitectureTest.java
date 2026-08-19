package com.bct.ngtpa.apiservice.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

class ControllerAdviceAndInternalBoundaryArchitectureTest {
  private static final Path OLD_ADVICE =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ApiExceptionHandler.java");
  private static final Path OLD_REFERENCE_DATE =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ReferenceDateController.java");

  @Test
  void approvedAdviceAndInternalBoundariesReplaceOldFlatSourceOwnership() throws Exception {
    Class<?> advice =
        Class.forName("com.bct.ngtpa.apiservice.adapter.in.web.advice.ApiExceptionHandler");
    Class<?> internalController =
        Class.forName(
            "com.bct.ngtpa.apiservice.adapter.in.web.internal.referencedate.ReferenceDateController");

    assertNotNull(advice.getAnnotation(RestControllerAdvice.class));
    assertNotNull(internalController.getAnnotation(RestController.class));
    assertFalse(Files.exists(OLD_ADVICE));
    assertFalse(Files.exists(OLD_REFERENCE_DATE));
    assertFalse(advice.getName().contains(".web.controller."));
    assertFalse(internalController.getName().contains(".web.controller."));
  }
}
