package com.bct.ngtpa.apiservice.testcontext;

import com.bct.ngtpa.apiservice.adapter.in.web.advice.ApiExceptionHandler;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorEventLogger;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorResponseRenderer;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ExceptionMappingRegistryConfig;
import com.bct.ngtpa.apiservice.adapter.in.web.error.PublicMessageResolver;
import com.bct.ngtpa.apiservice.adapter.out.config.ConfigBackedErrorMessageResolver;
import com.bct.ngtpa.apiservice.adapter.out.config.DefaultConfigVariantResolver;
import com.bct.ngtpa.apiservice.adapter.out.config.LocalizedConfigPropertiesConfiguration;
import com.bct.ngtpa.apiservice.adapter.out.config.LocalizedConfigSourceConfiguration;
import com.bct.ngtpa.apiservice.infrastructure.config.ConfigVariantResolverConfiguration;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.infrastructure.security.CorsProperties;
import com.bct.ngtpa.apiservice.infrastructure.security.SecurityConfig;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@SpringBootConfiguration
@EnableAutoConfiguration(
    excludeName =
        "org.springframework.boot.micrometer.metrics.autoconfigure.ssl.SslMetricsAutoConfiguration")
@EnableConfigurationProperties({CorsProperties.class, MaskingProperties.class})
@Import({
  SecurityConfig.class,
  ExceptionMappingRegistryConfig.class,
  ApiExceptionHandler.class,
  ErrorResponseRenderer.class,
  PublicMessageResolver.class,
  ErrorEventLogger.class,
  ConfigVariantResolverConfiguration.class,
  LocalizedConfigPropertiesConfiguration.class,
  LocalizedConfigSourceConfiguration.class,
  DefaultConfigVariantResolver.class,
  ConfigBackedErrorMessageResolver.class,
  MaskingService.class,
  LoggingSanitizer.class,
  UnknownRouteTestApplication.TestProbeController.class
})
public class UnknownRouteTestApplication {

  @Bean
  ObjectMapper objectMapper() {
    return new ObjectMapper();
  }

  @RestController
  static class TestProbeController {

    @GetMapping(path = "/api/v1/test/registered", produces = MediaType.TEXT_PLAIN_VALUE)
    Mono<String> registered() {
      return Mono.just("registered");
    }

    @GetMapping("/api/v1/test/unmapped-error")
    Mono<String> unmappedError() {
      return Mono.error(new IllegalStateException("independent catch-all case"));
    }
  }
}
