package com.bct.ngtpa.apiservice.adapter.out.referencedate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bct.ngtpa.apiservice.adapter.out.configserver.ReferenceDateProperties;
import com.bct.ngtpa.apiservice.application.port.out.ApimReferenceDateRefreshPort;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDateCacheUpdatePort;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

class ReferenceDateAdapterConstructorCompatibilityTest {

    @Test
    void existingEnvironmentConstructor_remainsAvailable() {
        Environment environment = new MockEnvironment();

        var adapter = new ReferenceDateAdapter(
                new ReferenceDateProperties(),
                Optional.<CachePort>empty(),
                "ngtpa",
                mock(ApimReferenceDateRefreshPort.class),
                mock(ReferenceDateCacheUpdatePort.class),
                environment,
                Clock.systemUTC());

        assertThat(adapter).isNotNull();
    }
}
