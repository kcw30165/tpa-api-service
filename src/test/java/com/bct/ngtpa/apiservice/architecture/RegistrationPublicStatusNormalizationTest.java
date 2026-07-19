package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationPublicStatusNormalizationTest {

    @Test
    void registrationWebMappersDoNotExposeDownstreamRejectedStatus() throws Exception {
        Path root = Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/registration/mapper");
        String sources;
        try (var paths = Files.walk(root)) {
            sources = paths.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> {
                        try {
                            return Files.readString(path);
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                .reduce("", (left, right) -> left + "\n" + right);
        }

        assertThat(sources)
                .doesNotContain("ApiStatus.DOWNSTREAM_REJECTED")
                .doesNotContain("DOWNSTREAM_REJECTION");
    }
}
