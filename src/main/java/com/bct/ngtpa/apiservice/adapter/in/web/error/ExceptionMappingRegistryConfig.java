package com.bct.ngtpa.apiservice.adapter.in.web.error;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Assembles the single {@link ExceptionMappingRegistry} bean from every per-capability {@code
 * *ExceptionMappings} group, plus the mandatory {@link CatchAllExceptionMapping}.
 *
 * <p>{@link AuthenticatedPortalContextExceptionMappings#authenticationRequired()} is registered in
 * this same shared registry, so the filter-path {@code
 * AuthenticatedPortalContextWebExceptionHandler} resolves its {@link ErrorDescriptor} from the same
 * bean as {@code ApiExceptionHandler}.
 *
 * <p>{@link ErrorDescriptor} carries a {@code List<ErrorEntry>} with a per-entry {@link
 * MessagePolicy}, so a mapping may express either a null-message contract (e.g. {@link
 * AccountBalanceExceptionMappings#retrievalFailure()}) or one resolved message per field error
 * (e.g. {@link WebExchangeBindExceptionMappings#validationFailed()}) while being registered like
 * every other mapping.
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
            PersonalInformationExceptionMappings.retrievalUnavailable(),
            PersonalInformationExceptionMappings.updateUnavailable(),
            CacheExceptionMappings.unexpected(),
            FundPriceExceptionMappings.retrievalUnavailable(),
            FundPriceExceptionMappings.invalidDate(),
            ApplicationExceptionMappings.invalidFundComparisonRequest(),
            FundComparisonExceptionMappings.retrievalUnavailable(),
            AuthenticatedPortalContextExceptionMappings.authenticationRequired(),
            AccountBalanceExceptionMappings.retrievalFailure(),
            AccountGainLossExceptionMappings.retrievalFailure(),
            FundFactSheetExceptionMappings.retrievalUnavailable(),
            WebExchangeBindExceptionMappings.validationFailed());
    return new ExceptionMappingRegistry(mappings, CatchAllExceptionMapping.INSTANCE);
  }
}
