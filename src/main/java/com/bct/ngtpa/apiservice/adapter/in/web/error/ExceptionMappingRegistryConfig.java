package com.bct.ngtpa.apiservice.adapter.in.web.error;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sub-task 1.4: assembles the single {@link ExceptionMappingRegistry} bean from every
 * per-capability {@code *ExceptionMappings} group, plus the mandatory {@link
 * CatchAllExceptionMapping}.
 *
 * <p>Sub-task 1.5 added {@link
 * AuthenticatedPortalContextExceptionMappings#authenticationRequired()} to this same shared
 * registry, so the filter-path {@code AuthenticatedPortalContextWebExceptionHandler} resolves its
 * {@link ErrorDescriptor} from the same bean as {@code ApiExceptionHandler}.
 *
 * <p>Sub-task 1.7 (M1 closure, Option A) added the 4 mappings that previously stayed inline in
 * {@code ApiExceptionHandler} because {@link ErrorDescriptor} could only express one error entry
 * with a single, always-resolved message: {@link
 * AccountBalanceExceptionMappings#retrievalFailure()}, {@link
 * AccountGainLossExceptionMappings#retrievalFailure()}, {@link
 * FundFactSheetExceptionMappings#retrievalUnavailable()} and {@link
 * WebExchangeBindExceptionMappings#validationFailed()}. {@link ErrorDescriptor} now carries a
 * {@code List<ErrorEntry>} with a per-entry {@link MessagePolicy}, which lets these four keep their
 * exact pre-existing public contract (either a null message, for the first three, or one resolved
 * message per field error, for the fourth) while being registered like every other mapping. Every
 * {@code *ExceptionMappings} class now registers exactly one mapping per exception type it
 * previously handled inline in {@code ApiExceptionHandler}, bringing the total to 21.
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
            FundComparisonExceptionMappings.retrievalUnavailable(),
            AuthenticatedPortalContextExceptionMappings.authenticationRequired(),
            AccountBalanceExceptionMappings.retrievalFailure(),
            AccountGainLossExceptionMappings.retrievalFailure(),
            FundFactSheetExceptionMappings.retrievalUnavailable(),
            WebExchangeBindExceptionMappings.validationFailed());
    return new ExceptionMappingRegistry(mappings, CatchAllExceptionMapping.INSTANCE);
  }
}
