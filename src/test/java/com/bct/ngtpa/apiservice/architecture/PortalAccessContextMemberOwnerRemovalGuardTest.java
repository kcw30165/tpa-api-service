package com.bct.ngtpa.apiservice.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PortalAccessContextMemberOwnerRemovalGuardTest {

    @Test
    void mainSourceDoesNotReferenceRemovedMemberOwnerContextTypeOrAccessor() throws Exception {
        try (Stream<Path> paths = Files.walk(Path.of("src/main/java"))) {
            for (Path path : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                assertFalse(source.contains("MemberOwnerContext"),
                        path + " must not reference removed MemberOwnerContext");
                assertFalse(source.contains("memberOwner()"),
                        path + " must not call removed PortalAccessContext.memberOwner()");
            }
        }
    }

    @Test
    void removedMemberOwnerContextSourceFileDoesNotExist() {
        assertFalse(Files.exists(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/dto/MemberOwnerContext.java")));
    }
}
