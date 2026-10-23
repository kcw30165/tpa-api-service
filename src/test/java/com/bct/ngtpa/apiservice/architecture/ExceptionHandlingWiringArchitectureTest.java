package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bct.ngtpa.apiservice.adapter.in.web.advice.ApiExceptionHandler;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorEventLogger;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorResponseRenderer;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ExceptionMappingRegistry;
import com.bct.ngtpa.apiservice.adapter.in.web.error.PublicMessageResolver;
import com.bct.ngtpa.apiservice.adapter.in.web.error.WorkflowErrorContractConfig;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.AuthenticatedPortalContextWebExceptionHandler;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.PortalAccessContextWebFilter;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingWebFilter;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequiredRequestIdWebFilter;
import com.bct.ngtpa.apiservice.infrastructure.logging.AccountBalanceObservabilityLogger;
import com.bct.ngtpa.apiservice.infrastructure.logging.AccountBalancePresentationEventLogger;
import com.bct.ngtpa.apiservice.infrastructure.logging.ExecutionLoggingAspect;
import com.bct.ngtpa.apiservice.infrastructure.logging.FundFactSheetPresentationEventLogger;
import com.bct.ngtpa.apiservice.infrastructure.logging.GeneralMetadataObservabilityLogger;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.infrastructure.logging.StructuredFailureEventLogger;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * M1 closure sub-task 1.11, item 6: a real Spring wiring test for the exception-handling bean
 * graph. Uses {@link ComponentScan}, not explicit {@code withBean(Class)} registration of the
 * target beans themselves, so that a missing {@code @Component} on any of them fails this test the
 * same way it fails application startup -- the exact gap that let {@link PublicMessageResolver}
 * reach the M1 milestone-gate smoke test without a {@code @Component} annotation, since {@code mvn
 * test} never boots a Spring context.
 */
class ExceptionHandlingWiringArchitectureTest {

  @Test
  void exceptionHandlingBeanGraphLoadsFromARealSpringContext() {
    new ApplicationContextRunner()
        .withUserConfiguration(WiringTestConfig.class)
        .withBean(ObjectMapper.class, ObjectMapper::new)
        .withBean(MaskingService.class, () -> new MaskingService(new MaskingProperties()))
        .withBean(ErrorMessageResolver.class, () -> mock(ErrorMessageResolver.class))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(ApiExceptionHandler.class);
              assertThat(context)
                  .hasSingleBean(AuthenticatedPortalContextWebExceptionHandler.class);
              assertThat(context).hasSingleBean(ExceptionMappingRegistry.class);
              assertThat(context).hasSingleBean(PublicMessageResolver.class);
              assertThat(context).hasSingleBean(ErrorEventLogger.class);
              assertThat(context).hasSingleBean(ErrorResponseRenderer.class);
              assertThat(context).hasSingleBean(LoggingSanitizer.class);
            });
  }

  @Configuration
  @ComponentScan(
      basePackageClasses = {
        ApiExceptionHandler.class,
        AuthenticatedPortalContextWebExceptionHandler.class,
        PublicMessageResolver.class,
        LoggingSanitizer.class
      },
      excludeFilters =
          @ComponentScan.Filter(
              type = FilterType.ASSIGNABLE_TYPE,
              classes = {
                AccountBalanceObservabilityLogger.class,
                AccountBalancePresentationEventLogger.class,
                ExecutionLoggingAspect.class,
                FundFactSheetPresentationEventLogger.class,
                GeneralMetadataObservabilityLogger.class,
                PortalAccessContextWebFilter.class,
                RequestLoggingWebFilter.class,
                RequiredRequestIdWebFilter.class,
                StructuredFailureEventLogger.class,
                WorkflowErrorContractConfig.class
              }))
  static class WiringTestConfig {}
}
