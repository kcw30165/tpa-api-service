package com.bct.ngtpa.apiservice.adapter.out.apim.retry;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ApimRetryClassificationArchitectureTest {

    @Test
    void classificationDoesNotImplementTransportRetryPrematurely() throws Exception {
        String facade = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimWebClientFacade.java"));
        assertThat(facade).doesNotContain("retryWhen(");
    }

    @Test
    void retryConfigurationIsOwnedByTheExistingApimProperties() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/config/ApimProperties.java"));
        assertThat(source).contains(
                "private final Retry retry = new Retry()",
                "private int maxAttempts = 3",
                "private long backoffMilliseconds");
    }
}
