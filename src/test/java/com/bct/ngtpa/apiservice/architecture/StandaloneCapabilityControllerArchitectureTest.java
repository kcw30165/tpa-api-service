package com.bct.ngtpa.apiservice.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

class StandaloneCapabilityControllerArchitectureTest {
  private record Boundary(String fqcn, String source, String oldSource) {}

  @Test
  void standaloneControllersUseApprovedCapabilityPackagesAndNames() throws Exception {
    List<Boundary> boundaries =
        List.of(
            new Boundary(
                "com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.CaptchaController",
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/captcha/CaptchaController.java",
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthCaptchaController.java"),
            new Boundary(
                "com.bct.ngtpa.apiservice.adapter.in.web.auth.logout.LogoutController",
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/logout/LogoutController.java",
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthLogoutController.java"),
            new Boundary(
                "com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryController",
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/usernamerecovery/UsernameRecoveryController.java",
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthUsernameRecoveryController.java"),
            new Boundary(
                "com.bct.ngtpa.apiservice.adapter.in.web.contribution.ContributionController",
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/contribution/ContributionController.java",
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ContributionController.java"),
            new Boundary(
                "com.bct.ngtpa.apiservice.adapter.in.web.notification.NotificationController",
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/notification/NotificationController.java",
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/NotificationController.java"),
            new Boundary(
                "com.bct.ngtpa.apiservice.adapter.in.web.personalinformation.PersonalInformationController",
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/personalinformation/PersonalInformationController.java",
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/PersonalInformationController.java"),
            new Boundary(
                "com.bct.ngtpa.apiservice.adapter.in.web.referencedata.ReferenceDataController",
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/referencedata/ReferenceDataController.java",
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ReferenceDataController.java"));
    for (Boundary boundary : boundaries) {
      Class<?> type = Class.forName(boundary.fqcn());
      assertNotNull(type.getAnnotation(RestController.class));
      assertFalse(Files.notExists(Path.of(boundary.source())));
      assertFalse(Files.exists(Path.of(boundary.oldSource())));
    }
  }
}
