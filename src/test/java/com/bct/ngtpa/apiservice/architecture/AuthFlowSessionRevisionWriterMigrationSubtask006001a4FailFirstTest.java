package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class AuthFlowSessionRevisionWriterMigrationSubtask006001a4FailFirstTest {
    @Test
    void productionDoesNotInvokeUnversionedSessionMutations() throws Exception {
        List<String> violations = new ArrayList<>();
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            paths.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.endsWith("AuthFlowSessionPort.java")
                            && !p.endsWith("RedisAuthFlowSessionAdapter.java"))
                    .forEach(p -> {
                        try {
                            String s = Files.readString(p);
                            if (s.matches(
                                    "(?s).*(authFlowSessionPort|sessions)\\.(update|touch|complete|cancel|delete)\\(.*"))
                                violations.add(p.toString());
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
        assertThat(violations).isEmpty();
    }
}
