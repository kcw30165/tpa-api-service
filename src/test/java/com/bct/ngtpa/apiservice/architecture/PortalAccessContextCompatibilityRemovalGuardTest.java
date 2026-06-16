package com.bct.ngtpa.apiservice.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PortalAccessContextCompatibilityRemovalGuardTest {

    @Test
    void compatibilityBridgesAreRemovedFromMainCode() throws Exception {
        List<String> forbidden = List.of(
                "ignoredLegacySelectedAccount",
                "ignoredLegacyContextDependency",
                "new AccountContext(headersContext.accountRef()",
                "new AccountContext(requestHeaderContext.accountRef()",
                "currentPortalAccessContextProvider == null",
                "Account-Ref is required for personal information update.",
                "toCommand(String ignoredLegacySelectedAccount",
                "command.accountRef()");

        List<Path> offenders;
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            offenders = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> containsAny(path, forbidden))
                    .toList();
        }

        assertTrue(offenders.isEmpty(), "Compatibility bridge tokens remain in main code: " + offenders);
    }

    private boolean containsAny(Path path, List<String> tokens) {
        try {
            String source = Files.readString(path);
            return tokens.stream().anyMatch(source::contains);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read " + path, exception);
        }
    }
}
