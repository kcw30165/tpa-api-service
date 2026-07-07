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
