package com.bct.ngtpa.apiservice.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CheckRegistrationUsernameUseCaseArchitectureTest {
    @Test
    void usernameCheckUseCaseDoesNotUseIdempotencyOrPolicyCertFields() throws IOException {
        Path service = Path.of("src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/username/CheckRegistrationUsernameService.java");
        if (!Files.exists(service)) {
            throw new AssertionError("Expected service file to exist: " + service);
        }
        String source = Files.readString(service);
        assertFalse(source.contains("RegistrationIdempotency"), "username/check must not use idempotency");
        assertFalse(source.contains("policyNo"), "username/check selectedTrust must not use policyNo");
        assertFalse(source.contains("certNo"), "username/check selectedTrust must not use certNo");
    }
}
