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
import com.bct.ngtpa.apiservice.application.exception.FundFactSheetRetrievalException;
import com.bct.ngtpa.apiservice.application.exception.FundPriceInvalidDateException;
import com.bct.ngtpa.apiservice.application.exception.FundPriceRetrievalException;
import com.bct.ngtpa.apiservice.application.exception.InvalidContributionRequestException;
import com.bct.ngtpa.apiservice.application.exception.InvalidNotificationRequestException;
import com.bct.ngtpa.apiservice.application.exception.InvalidPersonalInformationUpdateException;
import com.bct.ngtpa.apiservice.application.exception.PersonalInformationRetrievalException;
import com.bct.ngtpa.apiservice.application.exception.PersonalInformationUpdateException;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedPortalContextResolutionException;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.exception.ApimProfileResolutionException;
import com.bct.ngtpa.apiservice.exception.CacheException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;

/**
 * Sub-task 1.4: table-driven unit tests for every per-capability {@code *ExceptionMappings} group
 * and the mandatory {@link CatchAllExceptionMapping}, verifying each produces the same {@link
 * ErrorDescriptor} fields and diagnostic message as the pre-1.4 handler row it replaces (see
 * docs/design/15-exception-handling/01-current-state.md). Also verifies {@link
 * ExceptionMappingRegistryConfig#exceptionMappingRegistry()} assembles exactly these mappings plus
 * the catch-all, and that the full registry resolves correctly end to end.
 *
 * <p>Sub-task 1.5 added {@link AuthenticatedPortalContextExceptionMappings}.
 *
 * <p>Sub-task 1.7 (M1 closure, Option A) rewrite: every assertion that used to read {@code
 * descriptor.errorType()}/{@code .errorCode()}/{@code .targets()} directly now reads {@code
 * descriptor.errors().getFirst().type()}/{@code .code()}/{@code .targets()}, since {@link
 * ErrorDescriptor} now carries a {@code List<ErrorEntry>}. This sub-task also adds the 4 mappings
 * that previously stayed inline in {@code ApiExceptionHandler}: {@link
 * AccountBalanceExceptionMappings}, {@link FundFactSheetExceptionMappings} and {@link
 * WebExchangeBindExceptionMappings} are covered here; {@link AccountGainLossExceptionMappings} has
 * its own dedicated test class, {@code AccountGainLossExceptionMappingsTest}, per
 * docs/design/15-exception-handling/05-milestones.md row 1.7. {@code mappingCount()} moves from 17
 * to 21.
 */
class ExceptionMappingsTest {

  private static final ErrorContext CONTEXT = new ErrorContext("GET", "/api/v1/test", "req-1");

  private static ErrorEntry firstEntry(ErrorDescriptor descriptor) {
    return descriptor.errors().getFirst();
  }

  @Test
  void registrationLookupDownstreamMapping() {
    var mapping = RegistrationExceptionMappings.lookupDownstream();
    var exception =
        new RegistrationLookupDownstreamException(
            RegistrationLookupDownstreamException.Reason.UNEXPECTED_ERROR, "err.tpa.unexpected");

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);
    ErrorEntry entry = firstEntry(descriptor);

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, descriptor.httpStatus());
    assertEquals(ApiStatus.SYSTEM_ERROR, descriptor.apiStatus());
    assertEquals("DOWNSTREAM_SYSTEM", entry.type());
    assertEquals(SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE.value(), entry.code());
    assertEquals(List.of("PAGE"), entry.targets());
    assertEquals(MessagePolicy.RESOLVE, entry.policy());
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
    ErrorEntry entry = firstEntry(descriptor);

    assertEquals(exception.getPublicStatus(), descriptor.httpStatus());
    assertEquals(ApiStatus.SYSTEM_ERROR, descriptor.apiStatus());
    assertEquals("SYSTEM", entry.type());
    assertEquals(exception.getInternalCode(), entry.code());
    assertEquals(LogLevel.ERROR, descriptor.logLevel());
    assertEquals("APIM profile resolution failed", mapping.diagnosticMessage(exception));
  }

  @Test
  void apimUpstreamMapping() {
    var mapping = ApimExceptionMappings.upstream();
    var exception =
        new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_UPSTREAM_FAILURE, "fail");

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);
    ErrorEntry entry = firstEntry(descriptor);

    assertEquals(HttpStatus.BAD_GATEWAY, descriptor.httpStatus());
    assertEquals(ApiStatus.SYSTEM_ERROR, descriptor.apiStatus());
    assertEquals("SYSTEM", entry.type());
    assertEquals(ErrorCodes.APIM_UPSTREAM_FAILURE, entry.code());
    assertEquals(LogLevel.ERROR, descriptor.logLevel());
  }

  @Test
  void apimUpstreamMappingFallsBackToFixedCodeWhenExceptionCodeBlank() {
    var mapping = ApimExceptionMappings.upstream();
    var exception = new ApimException(HttpStatus.BAD_GATEWAY, "", "fail");

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);

    assertEquals(ErrorCodes.APIM_UPSTREAM_FAILURE, firstEntry(descriptor).code());
  }

  @Test
  void portalAccessContextResolutionMapping() {
    var mapping = PortalAccessExceptionMappings.resolutionFailure();
    var exception =
        new PortalAccessContextResolutionException(
            ErrorCodes.MEMBER_CONTEXT_INVALID, "Missing Account-Ref header");

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);
    ErrorEntry entry = firstEntry(descriptor);

    assertEquals(HttpStatus.BAD_REQUEST, descriptor.httpStatus());
    assertEquals(ApiStatus.BUSINESS_REJECTED, descriptor.apiStatus());
    assertEquals("BUSINESS", entry.type());
    assertEquals(ErrorCodes.MEMBER_CONTEXT_INVALID, entry.code());
    assertEquals(LogLevel.WARN, descriptor.logLevel());
  }

  @Test
  void genericApplicationExceptionMapping() {
    var mapping = ApplicationExceptionMappings.generic();
    var exception = new ApplicationException(ErrorCodes.REQUEST_INVALID, "rejected");

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);
    ErrorEntry entry = firstEntry(descriptor);

    assertEquals(HttpStatus.BAD_REQUEST, descriptor.httpStatus());
    assertEquals(ApiStatus.BUSINESS_REJECTED, descriptor.apiStatus());
    assertEquals("BUSINESS", entry.type());
    assertEquals(ErrorCodes.REQUEST_INVALID, entry.code());
    assertEquals(LogLevel.WARN, descriptor.logLevel());
  }

  @Test
  void invalidNotificationRequestMapping() {
    var mapping = ApplicationExceptionMappings.invalidNotificationRequest();
    var exception = new InvalidNotificationRequestException("invalid");

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);
    ErrorEntry entry = firstEntry(descriptor);

    assertEquals(ApiStatus.VALIDATION_FAILED, descriptor.apiStatus());
    assertEquals("FORM", entry.type());
    assertEquals(exception.getErrorCode(), entry.code());
  }

  @Test
  void invalidContributionRequestMapping() {
    var mapping = ApplicationExceptionMappings.invalidContributionRequest();
    var exception = new InvalidContributionRequestException("invalid");

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);
    ErrorEntry entry = firstEntry(descriptor);

    assertEquals(ApiStatus.VALIDATION_FAILED, descriptor.apiStatus());
    assertEquals("FORM", entry.type());
    assertEquals(exception.getErrorCode(), entry.code());
  }

  @Test
  void invalidFundComparisonRequestMapping() {
    var mapping = ApplicationExceptionMappings.invalidFundComparisonRequest();
    var exception = new FundComparisonRequestInvalidException("invalid");

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);
    ErrorEntry entry = firstEntry(descriptor);

    assertEquals(ApiStatus.VALIDATION_FAILED, descriptor.apiStatus());
    assertEquals("VALIDATION", entry.type());
    assertEquals(exception.getErrorCode(), entry.code());
  }

  @Test
  void authenticationRequiredMapping() {
    var mapping = SecurityExceptionMappings.authenticationRequired();
    var exception = new BadCredentialsException("bad credentials");

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);
    ErrorEntry entry = firstEntry(descriptor);

    assertEquals(HttpStatus.UNAUTHORIZED, descriptor.httpStatus());
    assertEquals(ApiStatus.BUSINESS_REJECTED, descriptor.apiStatus());
    assertEquals(ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED, entry.code());
    assertEquals(LogLevel.WARN, descriptor.logLevel());
  }

  @Test
  void accessDeniedMapping() {
    var mapping = SecurityExceptionMappings.accessDenied();
    var exception = new AccessDeniedException("denied");

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);

    assertEquals(HttpStatus.FORBIDDEN, descriptor.httpStatus());
    assertEquals(ErrorCodes.SECURITY_ACCESS_DENIED, firstEntry(descriptor).code());
    assertEquals(LogLevel.WARN, descriptor.logLevel());
  }

  @Test
  void malformedBodyMappingUsesReasonWhenPresent() {
    var mapping = RequestValidationExceptionMappings.malformedBody();
    var exception = new ServerWebInputException("payload malformed");

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);

    assertEquals(HttpStatus.BAD_REQUEST, descriptor.httpStatus());
    assertEquals(ErrorCodes.REQUEST_BODY_MALFORMED, firstEntry(descriptor).code());
    assertEquals(LogLevel.WARN, descriptor.logLevel());
    assertEquals("payload malformed", mapping.diagnosticMessage(exception));
  }

  @Test
  void malformedBodyMappingDiagnosticMessageFallsBackToExceptionMessageWhenReasonBlank() {
    var mapping = RequestValidationExceptionMappings.malformedBody();
    var exception = new ServerWebInputException("");

    assertEquals(exception.getMessage(), mapping.diagnosticMessage(exception));
  }

  @Test
  void invalidPersonalInformationUpdateMapping() {
    var mapping = PersonalInformationExceptionMappings.invalidUpdate();
    var exception = new InvalidPersonalInformationUpdateException("No fields were submitted.");

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);

    assertEquals(HttpStatus.BAD_REQUEST, descriptor.httpStatus());
    assertEquals(ApiStatus.VALIDATION_FAILED, descriptor.apiStatus());
    assertEquals(
        ErrorCodes.PERSONAL_INFORMATION_UPDATE_REQUEST_INVALID, firstEntry(descriptor).code());
  }

  @Test
  void personalInformationRetrievalUnavailableMapping() {
    var mapping = PersonalInformationExceptionMappings.retrievalUnavailable();
    var exception = PersonalInformationRetrievalException.systemFailure();

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);
    ErrorEntry entry = firstEntry(descriptor);

    assertEquals(PersonalInformationRetrievalException.class, mapping.exceptionType());
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, descriptor.httpStatus());
    assertEquals(ApiStatus.SYSTEM_ERROR, descriptor.apiStatus());
    assertEquals("SYSTEM", entry.type());
    assertEquals(ErrorCodes.PERSONAL_INFORMATION_RETRIEVAL_UNAVAILABLE, entry.code());
    assertEquals(List.of(), entry.targets());
    assertEquals(MessagePolicy.NONE, entry.policy());
    assertEquals(LogLevel.ERROR, descriptor.logLevel());
    assertEquals(false, descriptor.includeStackTrace());
    assertEquals("Personal information retrieval failed", mapping.diagnosticMessage(exception));
  }

  @Test
  void cacheExceptionMapping() {
    var mapping = CacheExceptionMappings.unexpected();
    var exception = new CacheException("cache failed", new RuntimeException("redis secret"));

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);
    ErrorEntry entry = firstEntry(descriptor);

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, descriptor.httpStatus());
    assertEquals("FORM", entry.type());
    assertEquals(ErrorCodes.CACHE_UNEXPECTED, entry.code());
    assertEquals(LogLevel.ERROR, descriptor.logLevel());
  }

  @Test
  void fundPriceRetrievalMapping() {
    var mapping = FundPriceExceptionMappings.retrievalUnavailable();
    var exception = FundPriceRetrievalException.systemFailure();

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, descriptor.httpStatus());
    assertEquals(ErrorCodes.FUND_PRICE_RETRIEVAL_UNAVAILABLE, firstEntry(descriptor).code());
    assertEquals(LogLevel.ERROR, descriptor.logLevel());
  }

  @Test
  void fundPriceInvalidDateMapping() {
    var mapping = FundPriceExceptionMappings.invalidDate();
    var exception = FundPriceInvalidDateException.forInvalidDate();

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);

    assertEquals(HttpStatus.BAD_REQUEST, descriptor.httpStatus());
    assertEquals(ErrorCodes.FUND_PRICE_REQUEST_INVALID, firstEntry(descriptor).code());
    assertEquals(LogLevel.WARN, descriptor.logLevel());
  }

  @Test
  void fundComparisonRetrievalMapping() {
    var mapping = FundComparisonExceptionMappings.retrievalUnavailable();
    var exception = FundComparisonRetrievalException.systemFailure();

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, descriptor.httpStatus());
    assertEquals(ErrorCodes.FUND_COMPARISON_RETRIEVAL_UNAVAILABLE, firstEntry(descriptor).code());
    assertEquals(LogLevel.ERROR, descriptor.logLevel());
  }

  @Test
  void authenticatedPortalContextResolutionMapping() {
    var mapping = AuthenticatedPortalContextExceptionMappings.authenticationRequired();
    var exception = new AuthenticatedPortalContextResolutionException();

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);
    ErrorEntry entry = firstEntry(descriptor);

    assertEquals(HttpStatus.UNAUTHORIZED, descriptor.httpStatus());
    assertEquals(ApiStatus.BUSINESS_REJECTED, descriptor.apiStatus());
    assertEquals("AUTHENTICATION_REQUIRED", entry.type());
    assertEquals(ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED, entry.code());
    assertEquals(List.of("PAGE"), entry.targets());
    assertEquals("authenticated.context.failure.mapped", descriptor.logEventName());
    assertEquals(LogLevel.WARN, descriptor.logLevel());
    assertEquals(false, descriptor.includeStackTrace());
  }

  @Test
  void catchAllMapping() {
    ErrorDescriptor descriptor =
        CatchAllExceptionMapping.INSTANCE.describe(new IllegalStateException("boom"), CONTEXT);
    ErrorEntry entry = firstEntry(descriptor);

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, descriptor.httpStatus());
    assertEquals(ApiStatus.SYSTEM_ERROR, descriptor.apiStatus());
    assertEquals("SYSTEM", entry.type());
    assertEquals(ErrorCodes.SYSTEM_UNEXPECTED, entry.code());
    assertEquals(LogLevel.ERROR, descriptor.logLevel());
    assertEquals(true, descriptor.includeStackTrace());
  }

  @Test
  void diagnosticMessageDefaultsToExceptionMessageWhenNotOverridden() {
    var mapping = SecurityExceptionMappings.accessDenied();
    var exception = new AccessDeniedException("denied reason");

    assertEquals("denied reason", mapping.diagnosticMessage(exception));
  }

  // ───────────────────────── Sub-task 1.7: AccountBalance, FundFactSheet, WebExchangeBind
  // ─────────────────────────

  // SonarQube fix (post-M1 quality gate, java:S1874): HttpStatus.UNPROCESSABLE_ENTITY is
  // deprecated by Spring 7 in favour of HttpStatus.UNPROCESSABLE_CONTENT, but the production
  // AccountBalanceExceptionMappings mapping (changing its public status/header behaviour, both
  // outside this branch's scope) still returns UNPROCESSABLE_ENTITY, and it is not yet confirmed
  // whether the production status should be renamed. Asserting UNPROCESSABLE_CONTENT instead
  // would compare against a different enum constant than what production actually returns.
  @SuppressWarnings("java:S1874")
  @Test
  void accountBalanceAccountNotFoundMappingHasNullMessagePolicy() {
    var mapping = AccountBalanceExceptionMappings.retrievalFailure();
    var exception = AccountBalanceRetrievalException.accountNotFound();

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);
    ErrorEntry entry = firstEntry(descriptor);

    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, descriptor.httpStatus());
    assertEquals(ApiStatus.BUSINESS_REJECTED, descriptor.apiStatus());
    assertEquals("BUSINESS", entry.type());
    assertEquals("err.ab.account.not.exist", entry.code());
    assertEquals(MessagePolicy.NONE, entry.policy());
    assertEquals(LogLevel.WARN, descriptor.logLevel());
    assertTrue(descriptor.extraLogFields().isEmpty());
  }

  @Test
  void accountBalanceSystemFailureMappingLogsAtError() {
    var mapping = AccountBalanceExceptionMappings.retrievalFailure();
    var exception = AccountBalanceRetrievalException.systemFailure();

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, descriptor.httpStatus());
    assertEquals(ApiStatus.SYSTEM_ERROR, descriptor.apiStatus());
    assertEquals(LogLevel.ERROR, descriptor.logLevel());
    assertEquals(MessagePolicy.NONE, firstEntry(descriptor).policy());
  }

  @Test
  void accountBalanceNormalizationFailureMappingCarriesExtraLogFields() {
    var mapping = AccountBalanceExceptionMappings.retrievalFailure();
    var failure =
        new AccountBalanceRetrievalException.NormalizationFailure(
            AccountBalanceRetrievalException.Collection.FUND_DTL,
            3,
            AccountBalanceRetrievalException.Field.FUND_CODE,
            AccountBalanceRetrievalException.ValidationReason.EMPTY);
    var exception = AccountBalanceRetrievalException.normalizationFailure(failure);

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);

    assertEquals("fundDtl", descriptor.extraLogFields().get("collection"));
    assertEquals(3, descriptor.extraLogFields().get("rowIndex"));
    assertEquals("fund-code", descriptor.extraLogFields().get("field"));
    assertEquals("EMPTY", descriptor.extraLogFields().get("reason"));
  }

  // Sub-task 1.9 (coverage closure): the existing test above only exercises
  // Collection.FUND_DTL; Collection.ACC_BAL was never exercised, leaving
  // one switch arm uncovered in AccountBalanceExceptionMappings. Field and
  // ValidationReason are kept the same shape as the existing test to
  // isolate this to only the missing collection-switch arm.
  @Test
  void accountBalanceNormalizationFailureMappingCoversAccBalCollection() {
    var mapping = AccountBalanceExceptionMappings.retrievalFailure();
    var failure =
        new AccountBalanceRetrievalException.NormalizationFailure(
            AccountBalanceRetrievalException.Collection.ACC_BAL,
            7,
            AccountBalanceRetrievalException.Field.FUND_CODE,
            AccountBalanceRetrievalException.ValidationReason.EMPTY);
    var exception = AccountBalanceRetrievalException.normalizationFailure(failure);

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);

    assertEquals("accBal", descriptor.extraLogFields().get("collection"));
    assertEquals(7, descriptor.extraLogFields().get("rowIndex"));
    assertEquals("fund-code", descriptor.extraLogFields().get("field"));
    assertEquals("EMPTY", descriptor.extraLogFields().get("reason"));
  }

  @Test
  void fundFactSheetRetrievalMapping() {
    var mapping = FundFactSheetExceptionMappings.retrievalUnavailable();
    var exception = FundFactSheetRetrievalException.systemFailure();

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);
    ErrorEntry entry = firstEntry(descriptor);

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, descriptor.httpStatus());
    assertEquals(ApiStatus.SYSTEM_ERROR, descriptor.apiStatus());
    assertEquals("SYSTEM", entry.type());
    assertEquals(ErrorCodes.FUND_FACT_SHEET_RETRIEVAL_UNAVAILABLE, entry.code());
    assertEquals(MessagePolicy.NONE, entry.policy());
    assertEquals(LogLevel.ERROR, descriptor.logLevel());
  }

  @Test
  void webExchangeBindMappingProducesOneEntryPerFieldErrorInOrder() throws Exception {
    var mapping = WebExchangeBindExceptionMappings.validationFailed();
    var exception = bindExceptionWithTwoFieldErrors("notificationId", "status");

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);

    assertEquals(HttpStatus.BAD_REQUEST, descriptor.httpStatus());
    assertEquals(ApiStatus.VALIDATION_FAILED, descriptor.apiStatus());
    assertEquals(2, descriptor.errors().size());
    assertEquals(List.of("notificationId"), descriptor.errors().get(0).targets());
    assertEquals(List.of("status"), descriptor.errors().get(1).targets());
    assertEquals("FIELD", descriptor.errors().get(0).type());
    assertEquals(ErrorCodes.REQUEST_VALIDATION_FAILED, descriptor.errors().get(0).code());
    assertEquals(MessagePolicy.RESOLVE, descriptor.errors().get(0).policy());
    assertEquals(MessagePolicy.RESOLVE, descriptor.errors().get(1).policy());
  }

  @Test
  void webExchangeBindMappingProducesOneFormEntryWhenNoFieldErrors() throws Exception {
    var mapping = WebExchangeBindExceptionMappings.validationFailed();
    var exception = bindExceptionWithoutFieldErrors();

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);
    ErrorEntry entry = firstEntry(descriptor);

    assertEquals(1, descriptor.errors().size());
    assertEquals("FORM", entry.type());
    assertTrue(entry.targets().isEmpty());
    assertEquals(MessagePolicy.RESOLVE, entry.policy());
  }

  @Test
  void webExchangeBindDiagnosticMessageUsesFirstFieldDefaultMessage() throws Exception {
    var mapping = WebExchangeBindExceptionMappings.validationFailed();
    var exception =
        bindExceptionWithFieldError("notificationId", "notificationId must not be empty");

    assertEquals("notificationId must not be empty", mapping.diagnosticMessage(exception));
  }

  // Sub-task 1.11 (item 5, coverage closure): the private fieldTarget() helper's
  // StringUtils.hasText(fieldError.getField()) branch was never exercised with a
  // blank field name -- every existing test uses a non-blank field.
  @Test
  void webExchangeBindMappingProducesEmptyTargetsForBlankFieldName() throws Exception {
    var mapping = WebExchangeBindExceptionMappings.validationFailed();
    var exception = bindExceptionWithFieldError("", "must not be blank");

    ErrorDescriptor descriptor = mapping.describe(exception, CONTEXT);

    assertTrue(descriptor.errors().getFirst().targets().isEmpty());
  }

  // ───────────────────────── registry assembly ─────────────────────────

  @Test
  void configAssemblesExactlyTwentyThreeMappingsPlusCatchAll() {
    ExceptionMappingRegistry registry =
        new ExceptionMappingRegistryConfig().exceptionMappingRegistry();

    assertEquals(23, registry.mappingCount());

    ErrorDescriptor updateUnavailable =
        registry.resolve(
            PersonalInformationUpdateException.systemFailure(),
            new ErrorContext("PUT", "/api/v1/personal-information", "req-put"));
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, updateUnavailable.httpStatus());
    assertEquals(ApiStatus.SYSTEM_ERROR, updateUnavailable.apiStatus());
    assertEquals(
        ErrorCodes.PERSONAL_INFORMATION_UPDATE_UNAVAILABLE,
        updateUnavailable.errors().getFirst().code());
    assertEquals(MessagePolicy.NONE, updateUnavailable.errors().getFirst().policy());

    ErrorDescriptor fundComparison =
        registry.resolve(
            FundComparisonRetrievalException.systemFailure(),
            new ErrorContext("GET", "/api/v1/fund-comparison", "req-x"));
    assertEquals(
        ErrorCodes.FUND_COMPARISON_RETRIEVAL_UNAVAILABLE,
        fundComparison.errors().getFirst().code());

    ErrorDescriptor accountBalance =
        registry.resolve(
            AccountBalanceRetrievalException.accountNotFound(),
            new ErrorContext("GET", "/api/v1/account-balance", "req-z"));
    assertEquals("err.ab.account.not.exist", accountBalance.errors().getFirst().code());
    assertEquals(MessagePolicy.NONE, accountBalance.errors().getFirst().policy());

    ErrorDescriptor unmapped =
        registry.resolve(
            new IllegalStateException("not individually mapped"),
            new ErrorContext("GET", "/api/v1/unknown", "req-y"));
    assertEquals(ErrorCodes.SYSTEM_UNEXPECTED, unmapped.errors().getFirst().code());
  }

  // ───────────────────────── bind-exception test fixtures ─────────────────────────

  private static WebExchangeBindException bindExceptionWithFieldError(
      String fieldName, String defaultMessage) throws Exception {
    Method method =
        ExceptionMappingsTest.class.getDeclaredMethod("notificationPatch", TestRequest.class);
    MethodParameter parameter = new MethodParameter(method, 0);
    TestRequest target = new TestRequest();
    BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "request");
    bindingResult.addError(new FieldError("request", fieldName, defaultMessage));
    return new WebExchangeBindException(parameter, bindingResult);
  }

  private static WebExchangeBindException bindExceptionWithTwoFieldErrors(
      String firstField, String secondField) throws Exception {
    Method method =
        ExceptionMappingsTest.class.getDeclaredMethod("notificationPatch", TestRequest.class);
    MethodParameter parameter = new MethodParameter(method, 0);
    TestRequest target = new TestRequest();
    BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "request");
    bindingResult.addError(
        new FieldError("request", firstField, firstField + " must not be empty"));
    bindingResult.addError(
        new FieldError("request", secondField, secondField + " must not be empty"));
    return new WebExchangeBindException(parameter, bindingResult);
  }

  private static WebExchangeBindException bindExceptionWithoutFieldErrors() throws Exception {
    Method method =
        ExceptionMappingsTest.class.getDeclaredMethod("notificationPatch", TestRequest.class);
    MethodParameter parameter = new MethodParameter(method, 0);
    TestRequest target = new TestRequest();
    BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "request");
    return new WebExchangeBindException(parameter, bindingResult);
  }

  @SuppressWarnings("unused")
  private void notificationPatch(@RequestBody TestRequest request) {
    // Method signature used only to construct MethodParameter for WebExchangeBindException.
  }

  private static final class TestRequest {}
}
