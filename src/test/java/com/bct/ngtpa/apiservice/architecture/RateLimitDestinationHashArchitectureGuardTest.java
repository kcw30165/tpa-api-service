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

class RateLimitDestinationHashArchitectureGuardTest {

    @Test
    void destinationHashHelperHasNoSpringAdapterRedisOrWebDependencies() throws Exception {
        List<String> forbiddenImports = List.of(
                "import org.springframework.",
                "import com.bct.ngtpa.apiservice.adapter.",
                "import io.lettuce.",
                "import org.springframework.data.redis.",
                "import org.springframework.web.");

        try (Stream<Path> paths = Files.walk(Path.of("src/main/java/com/bct/ngtpa/apiservice/application/service/security/ratelimit"))) {
            var sources = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();

            for (Path source : sources) {
                String content = Files.readString(source);
                for (String forbiddenImport : forbiddenImports) {
                    assertFalse(content.contains(forbiddenImport),
                            source + " must not contain forbidden dependency " + forbiddenImport);
                }
            }
        }
    }

    @Test
    void p3_4DoesNotIntroduceEndpointOrCsrfComponents() throws Exception {
        Set<String> sourceFileNames;
        try (Stream<Path> paths = Files.walk(Path.of("src/main/java"))) {
            sourceFileNames = paths
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet());
        }

        assertTrue(sourceFileNames.stream().noneMatch(name -> name.equals("AuthRegistrationController.java")),
                "P3.4 must not introduce AuthRegistrationController");
        assertTrue(sourceFileNames.stream().noneMatch(name -> name.equals("AuthRegistrationOtpController.java")),
                "P3.4 must not introduce AuthRegistrationOtpController");
    }
}
