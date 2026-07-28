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

class CsrfFoundationArchitectureGuardTest {

    @Test
    void csrfFoundationHasNoSpringAdapterRedisOrWebDependencies() throws Exception {
        List<String> forbiddenImports = List.of(
                "import org.springframework.",
                "import com.bct.ngtpa.apiservice.adapter.",
                "import io.lettuce.",
                "import org.springframework.data.redis.",
                "import org.springframework.web.",
                "import jakarta.servlet.");

        try (Stream<Path> paths = Files.walk(Path.of("src/main/java/com/bct/ngtpa/apiservice/application"))) {
            var csrfSources = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/csrf/"))
                    .toList();

            for (Path source : csrfSources) {
                String content = Files.readString(source);
                for (String forbiddenImport : forbiddenImports) {
                    assertFalse(content.contains(forbiddenImport),
                            source + " must not contain forbidden dependency " + forbiddenImport);
                }
            }
        }
    }
}
