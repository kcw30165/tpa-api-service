package com.bct.ngtpa.apiservice.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class P3SecurityArchitectureGuardTest {

    @Test
    void p3ApplicationSecurityModelsRemainFrameworkFree() throws Exception {
        List<String> forbiddenImports = List.of(
                "import org.springframework.",
                "import com.bct.ngtpa.apiservice.adapter.",
                "import io.lettuce.",
                "import org.springframework.data.redis.",
                "import org.springframework.web.",
                "import org.slf4j.");

        try (Stream<Path> paths = Files.walk(Path.of("src/main/java/com/bct/ngtpa/apiservice/application"))) {
            var sources = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("security/ratelimit")
                            || path.toString().contains("security/csrf")
                            || path.toString().contains("security/audit")
                            || path.getFileName().toString().equals("RateLimitPort.java")
                            || path.getFileName().toString().equals("CsrfTokenPort.java")
                            || path.getFileName().toString().equals("SecurityAuditEventPort.java"))
                    .toList();

            for (Path source : sources) {
                String content = Files.readString(source);
                for (String forbiddenImport : forbiddenImports) {
                    assertFalse(content.contains(forbiddenImport), source + " must not contain " + forbiddenImport);
                }
            }
        }
    }

    @Test
    void p3RedisAdaptersDoNotDependOnWebApimSmsEmailAdapters() throws Exception {
        List<String> forbiddenImports = List.of(
                "import com.bct.ngtpa.apiservice.adapter.in.web.",
                "import com.bct.ngtpa.apiservice.adapter.out.apim.",
                "import com.bct.ngtpa.apiservice.adapter.out.sms.",
                "import com.bct.ngtpa.apiservice.adapter.out.email.");
        Path root = Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis");
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            var sources = paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
            for (Path source : sources) {
                String content = Files.readString(source);
                for (String forbiddenImport : forbiddenImports) {
                    assertFalse(content.contains(forbiddenImport), source + " must not contain " + forbiddenImport);
                }
            }
        }
    }

    @Test
    void p3DoesNotIntroduceRegistrationEndpointsApimRegistrationAdaptersCaptchaOrRuntimeFilters() throws Exception {
        Set<String> fileNames;
        try (Stream<Path> paths = Files.walk(Path.of("src/main/java"))) {
            fileNames = paths.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet());
        }

        assertTrue(fileNames.stream().noneMatch(name -> name.equals("AuthRegistrationController.java")));
        assertTrue(fileNames.stream().noneMatch(name -> name.equals("AuthRegistrationOtpController.java")));
        assertTrue(fileNames.stream().noneMatch(name -> name.equals("AuthCaptchaController.java")));
        assertTrue(fileNames.stream().noneMatch(name -> name.equals("ApimRegistrationVerificationAdapter.java")));
        assertTrue(fileNames.stream().noneMatch(name -> name.equals("ApimRegistrationCredentialAdapter.java")));
        assertTrue(fileNames.stream().noneMatch(name -> name.equals("CaptchaChallengePort.java")));
        assertTrue(fileNames.stream().noneMatch(name -> name.equals("CaptchaChallengeRedisAdapter.java")));
        assertTrue(fileNames.stream().noneMatch(name -> name.equals("RegistrationCsrfWebFilter.java")));
        assertTrue(fileNames.stream().noneMatch(name -> name.equals("RegistrationRateLimitWebFilter.java")));
        assertTrue(fileNames.stream().noneMatch(name -> name.equals("RegistrationSessionRedisAdapter.java")));
    }
}
