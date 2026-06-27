package com.bct.ngtpa.apiservice.application.service.security.csrf;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecureCsrfTokenGeneratorTest {

    private final SecureCsrfTokenGenerator generator = new SecureCsrfTokenGenerator();

    @Test
    void generatesOpaqueHighEntropyToken() {
        String workflowRef = "workflow-ref-123";
        String first = generator.generateToken();
        String second = generator.generateToken();

        assertThat(first).isNotBlank();
        assertThat(first).startsWith("csrf_");
        assertThat(first).doesNotContain(workflowRef);
        assertThat(first).doesNotContain(" ").doesNotContain("\t").doesNotContain("\n");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void generatedTokenIsHeaderSafe() {
        String token = generator.generateToken();

        assertThat(token).matches("^[A-Za-z0-9_.-]+$");
    }
}
