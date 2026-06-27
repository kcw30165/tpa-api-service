package com.bct.ngtpa.apiservice.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class P31RuntimeRateLimitComponentGuardTest {

    @Test
    void p3_1DoesNotIntroduceRuntimeRateLimitComponents() throws Exception {
        Set<String> sourceFileNames;
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            sourceFileNames = paths
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet());
        }

        assertTrue(sourceFileNames.stream().noneMatch(name -> name.equals("RateLimitPort.java")),
                "P3.1 must not introduce RateLimitPort");
        assertTrue(sourceFileNames.stream().noneMatch(name -> name.equals("RedisRateLimitAdapter.java")),
                "P3.1 must not introduce RedisRateLimitAdapter");
        assertTrue(sourceFileNames.stream().noneMatch(name -> name.contains("DestinationHash")),
                "P3.1 must not introduce destination hashing helpers");
        assertTrue(sourceFileNames.stream().noneMatch(name -> name.equals("CsrfTokenPort.java")),
                "P3.1 must not introduce CsrfTokenPort");
    }
}
