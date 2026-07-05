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

class SecurityAuditArchitectureGuardTest {

    @Test
    void securityAuditApplicationModelHasNoSpringAdapterRedisWebDependencies() throws Exception {
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
                    .filter(path -> path.toString().contains("/security/audit/")
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
    void securityAuditLoggingAdapterDoesNotDependOnWebApimRedisSmsEmailAdapters() throws Exception {
        List<String> forbiddenImports = List.of(
                "import com.bct.ngtpa.apiservice.adapter.in.web.",
                "import com.bct.ngtpa.apiservice.adapter.out.apim.",
                "import com.bct.ngtpa.apiservice.adapter.out.redis.",
                "import com.bct.ngtpa.apiservice.adapter.out.sms.",
                "import com.bct.ngtpa.apiservice.adapter.out.email.");

        try (Stream<Path> paths = Files.walk(Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/out/security/audit"))) {
            var sources = paths
                    .filter(Files::isRegularFile)
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
}
