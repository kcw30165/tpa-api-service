package com.bct.ngtpa.apiservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class CacheAdminPortContractTest {

    @Test
    void exposesCapabilityScopedFindAndEvictOperations() {
        Method find = method("findAllByCapability");
        Method evict = method("evictAllByCapability");

        assertThat(find.getReturnType()).isEqualTo(Flux.class);
        assertThat(find.getParameterTypes()).hasSize(1);
        assertThat(evict.getReturnType()).isEqualTo(Mono.class);
        assertThat(evict.getParameterTypes()).hasSize(1);
    }

    @Test
    void doesNotExposeRawPatternOrKeyspaceDeleteOperations() {
        assertThat(Arrays.stream(CacheAdminPort.class.getMethods())
                .map(Method::getName)
                .filter(name -> name.toLowerCase().contains("pattern")
                        || name.toLowerCase().contains("keyspace")
                        || name.toLowerCase().equals("deleteall"))
                .toList())
                .isEmpty();
    }

    private Method method(String name) {
        return Arrays.stream(CacheAdminPort.class.getMethods())
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing method: " + name));
    }
}
