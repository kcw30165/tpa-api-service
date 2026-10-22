package com.bct.ngtpa.apiservice.adapter.in.web.error;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sub-task 1.4: assembles the single {@link ExceptionMappingRegistry} bean from every
 * per-capability {@code *ExceptionMappings} group, plus the mandatory {@link
 * CatchAllExceptionMapping}. Exactly one entry is registered per exception type that was previously
 * handled inline in {@code ApiExceptionHandler}, excluding {@code
 * AccountBalanceRetrievalException}, {@code AccountGainLossRetrievalException}, {@code
 * WebExchangeBindException} and {@code FundFactSheetRetrievalException}, whose original handler
 * bodies have request- or policy-specific behaviour (dynamic multi-error responses, or a response
 * message that is always {@code null}) that does not fit the single-error {@link ErrorDescriptor}
 * shape without changing observable behaviour; those four keep their existing handler bodies in
 * {@code ApiExceptionHandler} unchanged.
 */
@Configuration
public class ExceptionMappingRegistryConfig {

  @Bean
  public ExceptionMappingRegistry exceptionMappingRegistry() {
    List<ExceptionMapping<? extends Throwable>> mappings =
        List.of(
            RegistrationExceptionMappings.lookupDownstream(),
            ApimExceptionMappings.profileResolution(),
            ApimExceptionMappings.upstream(),
            ApplicationExceptionMappings.invalidNotificationRequest(),
            ApplicationExceptionMappings.invalidContributionRequest(),
            PortalAccessExceptionMappings.resolutionFailure(),
            ApplicationExceptionMappings.generic(),
            RequestValidationExceptionMappings.malformedBody(),
            SecurityExceptionMappings.authenticationRequired(),
            SecurityExceptionMappings.accessDenied(),
            PersonalInformationExceptionMappings.invalidUpdate(),
            CacheExceptionMappings.unexpected(),
            FundPriceExceptionMappings.retrievalUnavailable(),
            FundPriceExceptionMappings.invalidDate(),
            ApplicationExceptionMappings.invalidFundComparisonRequest(),
            FundComparisonExceptionMappings.retrievalUnavailable());
    return new ExceptionMappingRegistry(mappings, CatchAllExceptionMapping.INSTANCE);
  }
}
