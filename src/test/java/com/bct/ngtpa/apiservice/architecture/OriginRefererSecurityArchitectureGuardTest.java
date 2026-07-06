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

class OriginRefererSecurityArchitectureGuardTest {

    @Test
    void p3_6DoesNotIntroduceRegistrationEndpoints() throws Exception {
        Set<String> sourceFileNames;
        try (Stream<Path> paths = Files.walk(Path.of("src/main/java"))) {
            sourceFileNames = paths
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet());
        }

        assertTrue(sourceFileNames.stream().noneMatch(name -> name.equals("AuthRegistrationOtpController.java")),
                "P3.6 must not introduce AuthRegistrationOtpController");
        assertTrue(sourceFileNames.stream().noneMatch(name -> name.equals("AuthCaptchaController.java")),
                "P3.6 must not introduce AuthCaptchaController");
    }

    @Test
    void originRefererValidatorHasNoAdapterRedisApimDependencies() throws Exception {
        List<String> forbiddenImports = List.of(
                "import com.bct.ngtpa.apiservice.adapter.",
                "import io.lettuce.",
                "import org.springframework.data.redis.",
                "import com.bct.ngtpa.apiservice.adapter.out.apim.");

        Path source = Path.of("src/main/java/com/bct/ngtpa/apiservice/application/service/security/csrf/OriginRefererValidator.java");
        String content = Files.readString(source);
        for (String forbiddenImport : forbiddenImports) {
            assertFalse(content.contains(forbiddenImport),
                    source + " must not contain forbidden dependency " + forbiddenImport);
        }
    }
}
