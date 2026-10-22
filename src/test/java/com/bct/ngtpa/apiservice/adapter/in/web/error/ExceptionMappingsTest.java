package com.bct.ngtpa.apiservice.adapter.in.web.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupDownstreamException;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.exception.AccountBalanceRetrievalException;
import com.bct.ngtpa.apiservice.application.exception.ApplicationException;
import com.bct.ngtpa.apiservice.application.exception.FundComparisonRequestInvalidException;
import com.bct.ngtpa.apiservice.application.exception.FundComparisonRetrievalException;
import com.bct.ngtpa.apiservice.application.exception.FundPriceInvalidDateException;
import com.bct.ngtpa.apiservice.application.exception.FundPriceRetrievalException;
import com.bct.ngtpa.apiservice.application.exception.InvalidContributionRequestException;
import com.bct.ngtpa.apiservice.application.exception.InvalidNotificationRequestException;
import com.bct.ngtpa.apiservice.application.exception.InvalidPersonalInformationUpdateException;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedPortalContextResolutionException;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.exception.ApimProfileResolutionException;
import com.bct.ngtpa.apiservice.exception.CacheException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.server.ServerWebInputException;

/**
 * Sub-task 1.4: table-driven unit tests for every per-capability {@code *ExceptionMappings} group
 * and the mandatory {@link CatchAllExceptionMapping}, verifying each produces the same {@link
 * ErrorDescriptor} fields and diagnostic message as the pre-1.4 handler row it replaces (see
 * docs/design/15-exception-handling/01-current-state.md). Also verifies {@link
 * ExceptionMappingRegistryConfig#exceptionMappingRegistry()} assembles exactly these mappings plus
 * the catch-all, and that the full registry resolves correctly end to end.
 *
 * <p>Sub-task 1.5 adds {@link AuthenticatedPortalContextExceptionMappings#authenticationRequired()}
 * to the same shared registry, bringing the total to 17 registered mappings.
 */
class ExceptionMappingsTest {

  private static final ErrorContext CONTEXT = new ErrorContext("GET", "/api/v1/test", "req-1");

  @Test
  void registrationLookupDownstreamMapping() {
    var mapping = RegistrationExceptionMappings.lookupDownstream();
    var exception =
        new RegistrationLookupDownstreamException(
            RegistrationLookupDownstreamException.Reason.UNEXPECTED_ERROR, "err.tpa.unexpected");

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, descriptor.httpStatus());
    assertEquals(ApiStatus.SYSTEM_ERROR, descriptor.apiStatus());
    assertEquals("DOWNSTREAM_SYSTEM", descriptor.errorType());
    assertEquals(SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE.value(), descriptor.errorCode());
    assertEquals(java.util.List.of("PAGE"), descriptor.targets());
    assertEquals(LogLevel.ERROR, descriptor.logLevel());
    assertEquals(false, descriptor.includeStackTrace());
    assertTrue(
        mapping.diagnosticMessage(exception).startsWith("TRPSearchMember contract failure: "));
  }

  @Test
  void apimProfileResolutionMapping() {
    var mapping = ApimExceptionMappings.profileResolution();
    var exception = new ApimProfileResolutionException("profile resolution failed");

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);

    assertEquals(exception.getPublicStatus(), descriptor.httpStatus());
    assertEquals(ApiStatus.SYSTEM_ERROR, descriptor.apiStatus());
    assertEquals("SYSTEM", descriptor.errorType());
    assertEquals(exception.getInternalCode(), descriptor.errorCode());
    assertEquals(LogLevel.ERROR, descriptor.logLevel());
    assertEquals("APIM profile resolution failed", mapping.diagnosticMessage(exception));
  }

  @Test
  void apimUpstreamMapping() {
    var mapping = ApimExceptionMappings.upstream();
    var exception =
        new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_UPSTREAM_FAILURE, "fail");

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);

    assertEquals(HttpStatus.BAD_GATEWAY, descriptor.httpStatus());
    assertEquals(ApiStatus.SYSTEM_ERROR, descriptor.apiStatus());
    assertEquals("DOWNSTREAM_SYSTEM", descriptor.errorType());
    assertEquals(ErrorCodes.APIM_UPSTREAM_FAILURE, descriptor.errorCode());
    assertEquals(LogLevel.ERROR, descriptor.logLevel());
  }

  @Test
  void apimUpstreamMappingFallsBackToFixedCodeWhenExceptionCodeBlank() {
    var mapping = ApimExceptionMappings.upstream();
    var exception = new ApimException(HttpStatus.BAD_GATEWAY, "", "fail");

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);

    assertEquals(ErrorCodes.APIM_UPSTREAM_FAILURE, descriptor.errorCode());
  }

  @Test
  void portalAccessContextResolutionMapping() {
    var mapping = PortalAccessExceptionMappings.resolutionFailure();
    var exception =
        new PortalAccessContextResolutionException(
            ErrorCodes.MEMBER_CONTEXT_INVALID, "Missing Account-Ref header");

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);

    assertEquals(HttpStatus.BAD_REQUEST, descriptor.httpStatus());
    assertEquals(ApiStatus.BUSINESS_REJECTED, descriptor.apiStatus());
    assertEquals("BUSINESS", descriptor.errorType());
    assertEquals(ErrorCodes.MEMBER_CONTEXT_INVALID, descriptor.errorCode());
    assertEquals(LogLevel.WARN, descriptor.logLevel());
  }

  @Test
  void genericApplicationExceptionMapping() {
    var mapping = ApplicationExceptionMappings.generic();
    var exception = new ApplicationException(ErrorCodes.REQUEST_INVALID, "rejected");

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);

    assertEquals(HttpStatus.BAD_REQUEST, descriptor.httpStatus());
    assertEquals(ApiStatus.BUSINESS_REJECTED, descriptor.apiStatus());
    assertEquals("BUSINESS", descriptor.errorType());
    assertEquals(ErrorCodes.REQUEST_INVALID, descriptor.errorCode());
    assertEquals(LogLevel.WARN, descriptor.logLevel());
  }

  @Test
  void invalidNotificationRequestMapping() {
    var mapping = ApplicationExceptionMappings.invalidNotificationRequest();
    var exception = new InvalidNotificationRequestException("invalid");

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);

    assertEquals(ApiStatus.VALIDATION_FAILED, descriptor.apiStatus());
    assertEquals("FORM", descriptor.errorType());
    assertEquals(exception.getErrorCode(), descriptor.errorCode());
  }

  @Test
  void invalidContributionRequestMapping() {
    var mapping = ApplicationExceptionMappings.invalidContributionRequest();
    var exception = new InvalidContributionRequestException("invalid");

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);

    assertEquals(ApiStatus.VALIDATION_FAILED, descriptor.apiStatus());
    assertEquals("FORM", descriptor.errorType());
    assertEquals(exception.getErrorCode(), descriptor.errorCode());
  }

  @Test
  void invalidFundComparisonRequestMapping() {
    var mapping = ApplicationExceptionMappings.invalidFundComparisonRequest();
    var exception = new FundComparisonRequestInvalidException("invalid");

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);

    assertEquals(ApiStatus.VALIDATION_FAILED, descriptor.apiStatus());
    assertEquals("VALIDATION", descriptor.errorType());
    assertEquals(exception.getErrorCode(), descriptor.errorCode());
  }

  @Test
  void authenticationRequiredMapping() {
    var mapping = SecurityExceptionMappings.authenticationRequired();
    var exception = new BadCredentialsException("bad credentials");

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);

    assertEquals(HttpStatus.UNAUTHORIZED, descriptor.httpStatus());
    assertEquals(ApiStatus.BUSINESS_REJECTED, descriptor.apiStatus());
    assertEquals(ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED, descriptor.errorCode());
    assertEquals(LogLevel.WARN, descriptor.logLevel());
  }

  @Test
  void accessDeniedMapping() {
    var mapping = SecurityExceptionMappings.accessDenied();
    var exception = new AccessDeniedException("denied");

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);

    assertEquals(HttpStatus.FORBIDDEN, descriptor.httpStatus());
    assertEquals(ErrorCodes.SECURITY_ACCESS_DENIED, descriptor.errorCode());
    assertEquals(LogLevel.WARN, descriptor.logLevel());
  }

  @Test
  void malformedBodyMappingUsesReasonWhenPresent() {
    var mapping = RequestValidationExceptionMappings.malformedBody();
    var exception = new ServerWebInputException("payload malformed");

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);

    assertEquals(HttpStatus.BAD_REQUEST, descriptor.httpStatus());
    assertEquals(ErrorCodes.REQUEST_BODY_MALFORMED, descriptor.errorCode());
    assertEquals(LogLevel.WARN, descriptor.logLevel());
    assertEquals("payload malformed", mapping.diagnosticMessage(exception));
  }

  @Test
  void invalidPersonalInformationUpdateMapping() {
    var mapping = PersonalInformationExceptionMappings.invalidUpdate();
    var exception = new InvalidPersonalInformationUpdateException("No fields were submitted.");

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);

    assertEquals(HttpStatus.BAD_REQUEST, descriptor.httpStatus());
    assertEquals(ApiStatus.VALIDATION_FAILED, descriptor.apiStatus());
    assertEquals(ErrorCodes.PERSONAL_INFORMATION_UPDATE_REQUEST_INVALID, descriptor.errorCode());
  }

  @Test
  void cacheExceptionMapping() {
    var mapping = CacheExceptionMappings.unexpected();
    var exception = new CacheException("cache failed", new RuntimeException("redis secret"));

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, descriptor.httpStatus());
    assertEquals("FORM", descriptor.errorType());
    assertEquals(ErrorCodes.CACHE_UNEXPECTED, descriptor.errorCode());
    assertEquals(LogLevel.ERROR, descriptor.logLevel());
  }

  @Test
  void fundPriceRetrievalMapping() {
    var mapping = FundPriceExceptionMappings.retrievalUnavailable();
    var exception = FundPriceRetrievalException.systemFailure();

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, descriptor.httpStatus());
    assertEquals(ErrorCodes.FUND_PRICE_RETRIEVAL_UNAVAILABLE, descriptor.errorCode());
    assertEquals(LogLevel.ERROR, descriptor.logLevel());
  }

  @Test
  void fundPriceInvalidDateMapping() {
    var mapping = FundPriceExceptionMappings.invalidDate();
    var exception = FundPriceInvalidDateException.forInvalidDate();

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);

    assertEquals(HttpStatus.BAD_REQUEST, descriptor.httpStatus());
    assertEquals(ErrorCodes.FUND_PRICE_REQUEST_INVALID, descriptor.errorCode());
    assertEquals(LogLevel.WARN, descriptor.logLevel());
  }

  @Test
  void fundComparisonRetrievalMapping() {
    var mapping = FundComparisonExceptionMappings.retrievalUnavailable();
    var exception = FundComparisonRetrievalException.systemFailure();

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, descriptor.httpStatus());
    assertEquals(ErrorCodes.FUND_COMPARISON_RETRIEVAL_UNAVAILABLE, descriptor.errorCode());
    assertEquals(LogLevel.ERROR, descriptor.logLevel());
  }

  @Test
  void catchAllMapping() {
    ErrorDescriptor descriptor =
        CatchAllExceptionMapping.INSTANCE.describe(new IllegalStateException("boom"), CONTEXT);

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, descriptor.httpStatus());
    assertEquals(ApiStatus.SYSTEM_ERROR, descriptor.apiStatus());
    assertEquals("SYSTEM", descriptor.errorType());
    assertEquals(ErrorCodes.SYSTEM_UNEXPECTED, descriptor.errorCode());
    assertEquals(LogLevel.ERROR, descriptor.logLevel());
    assertEquals(true, descriptor.includeStackTrace());
  }

  @Test
  void diagnosticMessageDefaultsToExceptionMessageWhenNotOverridden() {
    var mapping = SecurityExceptionMappings.accessDenied();
    var exception = new AccessDeniedException("denied reason");

    assertEquals("denied reason", mapping.diagnosticMessage(exception));
  }

  // ───────────────────────── Sub-task 1.5: filter-path mapping ─────────────────────────

  @Test
  void authenticatedPortalContextResolutionMapping() {
    var mapping = AuthenticatedPortalContextExceptionMappings.authenticationRequired();
    var exception = new AuthenticatedPortalContextResolutionException();

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);

    assertEquals(HttpStatus.UNAUTHORIZED, descriptor.httpStatus());
    assertEquals(ApiStatus.BUSINESS_REJECTED, descriptor.apiStatus());
    assertEquals("AUTHENTICATION_REQUIRED", descriptor.errorType());
    assertEquals(ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED, descriptor.errorCode());
    assertEquals(java.util.List.of("PAGE"), descriptor.targets());
    assertEquals("authenticated.context.failure.mapped", descriptor.logEventName());
    assertEquals(LogLevel.WARN, descriptor.logLevel());
    assertEquals(false, descriptor.includeStackTrace());
  }

  // ───────────────────────── registry assembly ─────────────────────────

  @Test
  void configAssemblesExactlySeventeenMappingsPlusCatchAll() {
    ExceptionMappingRegistry registry =
        new ExceptionMappingRegistryConfig().exceptionMappingRegistry();

    assertEquals(17, registry.mappingCount());

    // Spot-check resolution end to end for a representative subset.
    ErrorDescriptor fundComparison =
        registry.resolve(
            FundComparisonRetrievalException.systemFailure(),
            new ErrorContext("GET", "/api/v1/fund-comparison", "req-x"));
    assertEquals(ErrorCodes.FUND_COMPARISON_RETRIEVAL_UNAVAILABLE, fundComparison.errorCode());

    ErrorDescriptor authenticatedPortalContext =
        registry.resolve(
            new AuthenticatedPortalContextResolutionException(),
            new ErrorContext("GET", "/api/v1/fund-price", "req-w"));
    assertEquals(
        ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED, authenticatedPortalContext.errorCode());
    assertEquals(HttpStatus.UNAUTHORIZED, authenticatedPortalContext.httpStatus());

    ErrorDescriptor unmapped =
        registry.resolve(
            new IllegalStateException("not individually mapped"),
            new ErrorContext("GET", "/api/v1/unknown", "req-y"));
    assertEquals(ErrorCodes.SYSTEM_UNEXPECTED, unmapped.errorCode());
  }

  @Test
  void
      accountBalanceAndAccountGainLossAndWebExchangeBindAndFundFactSheetAreIntentionallyNotRegistered() {
    ExceptionMappingRegistry registry =
        new ExceptionMappingRegistryConfig().exceptionMappingRegistry();

    // These four keep their existing handler bodies (dynamic multi-error responses, or a
    // response message that is always null) per ExceptionMappingRegistryConfig's documented
    // scope decision; an AccountBalanceRetrievalException falls through to the catch-all here
    // because it is not individually registered.
    ErrorDescriptor fallback =
        registry.resolve(
            AccountBalanceRetrievalException.accountNotFound(),
            new ErrorContext("GET", "/api/v1/account-balance", "req-z"));
    assertEquals(ErrorCodes.SYSTEM_UNEXPECTED, fallback.errorCode());
  }
}
