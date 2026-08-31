package com.bct.ngtpa.apiservice.application.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.port.in.GeneralMetadataResolver;
import com.bct.ngtpa.apiservice.application.port.out.GeneralMetadataCachePort;
import com.bct.ngtpa.apiservice.application.port.out.GeneralMetadataSourcePort;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class GeneralMetadataBoundaryTest {
  @Test
  void reactivePortsReturnMono() {
    for (Class<?> type :
        new Class<?>[] {
          GeneralMetadataResolver.class,
          GeneralMetadataCachePort.class,
          GeneralMetadataSourcePort.class
        }) {
      for (Method method : type.getDeclaredMethods())
        assertThat(method.getReturnType()).isEqualTo(Mono.class);
    }
  }

  @Test
  void applicationContractsContainNoBlockingOrAdapterAnnotations() throws Exception {
    Path root = Path.of("src/main/java/com/bct/ngtpa/apiservice/application");
    try (var paths = Files.walk(root)) {
      var text =
          paths
              .filter(p -> p.toString().contains("metadata") && p.toString().endsWith(".java"))
              .map(
                  p -> {
                    try {
                      return Files.readString(p);
                    } catch (Exception e) {
                      throw new RuntimeException(e);
                    }
                  })
              .reduce("", String::concat);
      assertThat(text)
          .doesNotContain(".block(", "JsonProperty", "Redis", "ReactiveRedis", "ApimExchange");
    }
  }
}
