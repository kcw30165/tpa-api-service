package com.bct.ngtpa.apiservice.adapter.in.web.personalinformation;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedSessionCookieReader;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateResponseMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.request.UpdatePersonalInformationRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.FormPageResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.FormSchemaResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.HttpStatusClassification;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MappedError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.PersonalInformationResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.UpdatePersonalInformationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.support.RequestLanguageResolver;
import com.bct.ngtpa.apiservice.adapter.in.web.validation.PersonalInformationFieldAccessChecker;
import com.bct.ngtpa.apiservice.adapter.in.web.validation.PersonalInformationUpdateYamlValidator;
import com.bct.ngtpa.apiservice.application.dto.GetPersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
import com.bct.ngtpa.apiservice.application.port.in.GetPersonalInformationUseCase;
import com.bct.ngtpa.apiservice.application.port.in.UpdatePersonalInformationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

@RestController
@RequestMapping("/api/v1/personal-information")
@RequiredArgsConstructor
public class PersonalInformationController {

  private final GetPersonalInformationUseCase getPersonalInformationUseCase;
  private final PersonalInformationWebMapper personalInformationWebMapper;
  private final CurrentPortalAccessContextResolver currentPortalAccessContextResolver;

  private final UpdatePersonalInformationUseCase updatePersonalInformationUseCase;
  private final PersonalInformationUpdateWebMapper requestMapper;
  private final PersonalInformationUpdateResponseMapper responseMapper;
  private final PersonalInformationUpdateYamlValidator validator;
  private final AuthenticatedSessionCookieReader sessionCookieReader;
  private final PersonalInformationFieldAccessChecker accessChecker;

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<MutationResponse<PersonalInformationResultResponse>> getPersonalInformation(
      @RequestHeader(value = RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, required = false)
          String acceptLanguage,
      ServerWebExchange exchange) {
    return Mono.deferContextual(
        contextView -> {
          String language = resolveLanguage(contextView, acceptLanguage);
          String requestId = requestIdFrom(contextView);
          Optional<AuthenticatedSessionId> sessionId = sessionCookieReader.read(exchange);
          return currentPortalAccessContextResolver
              .current()
              .flatMap(
                  portalAccessContext -> {
                    String accountRef = requireAccountRef(portalAccessContext);
                    return getPersonalInformationUseCase
                        .executeAndCache(
                            new GetPersonalInformationCommand(
                                language,
                                portalAccessContext.account().accountEnv(),
                                portalAccessContext.account().policyNo(),
                                portalAccessContext.account().certNo(),
                                portalAccessContext.actor().actorUserId(),
                                portalAccessContext.actor().actorUserRole()),
                            sessionId,
                            accountRef,
                            requestId)
                        .map(
                            result ->
                                success(
                                    personalInformationWebMapper.toFormPageResponse(
                                        result,
                                        language,
                                        accountEnv(portalAccessContext),
                                        trustCode(portalAccessContext),
                                        schemeType(portalAccessContext))));
                  });
        });
  }

  @PutMapping
  public Mono<UpdatePersonalInformationResponse> update(
      @RequestBody Mono<UpdatePersonalInformationRequest> request,
      @RequestHeader(value = RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, required = false)
          String acceptLanguage,
      ServerWebExchange exchange) {
    return Mono.deferContextual(
        contextView ->
            currentPortalAccessContextResolver
                .current()
                .flatMap(
                    portalAccessContext ->
                        request.flatMap(
                            body -> {
                              String resolvedLocale = resolveLanguage(contextView, acceptLanguage);
                              String accountRef = requireAccountRef(portalAccessContext);
                              requestMapper.validateStructure(body);
                              List<ApiError> yamlErrors =
                                  validator == null
                                      ? List.of()
                                      : validator.validate(
                                          body,
                                          resolvedLocale,
                                          accountEnv(portalAccessContext),
                                          trustCode(portalAccessContext),
                                          schemeType(portalAccessContext));
                              return accessChecker
                                  .check(
                                      body,
                                      new GetPersonalInformationCommand(
                                          resolvedLocale,
                                          accountEnv(portalAccessContext),
                                          portalAccessContext.account().policyNo(),
                                          portalAccessContext.account().certNo(),
                                          portalAccessContext.actor().actorUserId(),
                                          portalAccessContext.actor().actorUserRole()),
                                      sessionCookieReader.read(exchange),
                                      accountRef,
                                      requestIdFrom(contextView))
                                  .flatMap(
                                      accessErrors -> {
                                        List<MappedError> errors =
                                            mergeErrors(yamlErrors, accessErrors);
                                        if (!errors.isEmpty()) {
                                          return Mono.<UpdatePersonalInformationResponse>just(
                                              withUpdateHttpStatus(
                                                  exchange,
                                                  UpdatePersonalInformationResponse
                                                      .validationFailed(errors)));
                                        }
                                        var mapped =
                                            requestMapper.toCommand(
                                                body.applyToAllAccounts(), body);
                                        var command =
                                            new com.bct.ngtpa.apiservice.application.dto
                                                .UpdatePersonalInformationCommand(
                                                mapped.applyToAllAccounts(),
                                                mapped.updateFields(),
                                                accountEnv(portalAccessContext),
                                                portalAccessContext.account().policyNo(),
                                                portalAccessContext.account().certNo(),
                                                portalAccessContext.actor().actorUserId(),
                                                portalAccessContext.actor().actorUserRole());
                                        return updatePersonalInformationUseCase
                                            .execute(command)
                                            .map(responseMapper::toResponse)
                                            .<UpdatePersonalInformationResponse>map(
                                                response ->
                                                    withUpdateHttpStatus(exchange, response));
                                      });
                            })));
  }

  private static MutationResponse<PersonalInformationResultResponse> success(
      FormPageResponse<FormSchemaResponse> formPage) {
    return MutationResponse.success(
        ApiStatus.SUCCESS, PersonalInformationResultResponse.from(formPage), List.of());
  }

  private static UpdatePersonalInformationResponse withUpdateHttpStatus(
      ServerWebExchange exchange, UpdatePersonalInformationResponse body) {
    exchange
        .getResponse()
        .setStatusCode(
            body.status() == ApiStatus.VALIDATION_FAILED
                ? HttpStatus.UNPROCESSABLE_CONTENT
                : HttpStatusClassification.classify(body.status()));
    return body;
  }

  private static List<MappedError> mergeErrors(List<ApiError> first, List<ApiError> second) {
    return Stream.concat(first.stream(), second.stream())
        .map(error -> new MappedError(error.type(), error.code(), error.targets()))
        .distinct()
        .toList();
  }

  private String requireAccountRef(PortalAccessContext context) {
    if (context == null
        || context.account() == null
        || !StringUtils.hasText(context.account().accountRef())) {
      throw new PortalAccessContextResolutionException(
          ErrorCodes.MEMBER_CONTEXT_INVALID, "Missing Account-Ref header for selected-account API");
    }
    return context.account().accountRef();
  }

  private String accountEnv(PortalAccessContext context) {
    return context == null || context.account() == null ? null : context.account().accountEnv();
  }

  private String trustCode(PortalAccessContext context) {
    return context == null || context.account() == null ? null : context.account().trustCode();
  }

  private String schemeType(PortalAccessContext context) {
    return context == null || context.account() == null ? null : context.account().schemeType();
  }

  private String requestIdFrom(ContextView contextView) {
    RequestHeaderContext requestHeaderContext =
        contextView.getOrDefault(RequestHeaderContextKeys.CONTEXT_KEY, null);
    return requestHeaderContext == null ? null : requestHeaderContext.requestId();
  }

  private String resolveLanguage(ContextView contextView, String acceptLanguage) {
    RequestHeaderContext requestHeaderContext =
        contextView.getOrDefault(RequestHeaderContextKeys.CONTEXT_KEY, null);
    return RequestLanguageResolver.resolve(requestHeaderContext, acceptLanguage, null);
  }
}
