package com.bct.ngtpa.apiservice.adapter.in.web.personalinformation;

import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateResponseMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.request.UpdatePersonalInformationRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.FormPageResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.FormSchemaResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.PersonalInformationUpdateResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.support.RequestLanguageResolver;
import com.bct.ngtpa.apiservice.adapter.in.web.validation.PersonalInformationUpdateYamlValidator;
import com.bct.ngtpa.apiservice.application.dto.GetPersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
import com.bct.ngtpa.apiservice.application.port.in.GetPersonalInformationUseCase;
import com.bct.ngtpa.apiservice.application.port.in.UpdatePersonalInformationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<FormPageResponse<FormSchemaResponse>> getPersonalInformation(
      @RequestHeader(value = RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, required = false)
          String acceptLanguage) {
    return Mono.deferContextual(
        contextView -> {
          String language = resolveLanguage(contextView, acceptLanguage);
          return currentPortalAccessContextResolver
              .current()
              .flatMap(
                  portalAccessContext -> {
                    requireAccountRef(portalAccessContext);
                    return getPersonalInformationUseCase
                        .execute(
                            new GetPersonalInformationCommand(
                                language,
                                portalAccessContext.account().accountEnv(),
                                portalAccessContext.account().policyNo(),
                                portalAccessContext.account().certNo(),
                                portalAccessContext.actor().actorUserId(),
                                portalAccessContext.actor().actorUserRole()))
                        .map(
                            result ->
                                personalInformationWebMapper.toFormPageResponse(
                                    result,
                                    language,
                                    accountEnv(portalAccessContext),
                                    trustCode(portalAccessContext),
                                    schemeType(portalAccessContext)));
                  });
        });
  }

  @PutMapping
  public Mono<MutationResponse<List<PersonalInformationUpdateResultResponse>>> update(
      @RequestBody Mono<UpdatePersonalInformationRequest> request,
      @RequestHeader(value = RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, required = false)
          String acceptLanguage) {
    return Mono.deferContextual(
        contextView ->
            currentPortalAccessContextResolver
                .current()
                .flatMap(
                    portalAccessContext ->
                        request.flatMap(
                            body -> {
                              String resolvedLocale = resolveLanguage(contextView, acceptLanguage);
                              List<ApiError> errors =
                                  validator == null
                                      ? List.of()
                                      : validator.validate(
                                          body,
                                          resolvedLocale,
                                          accountEnv(portalAccessContext),
                                          trustCode(portalAccessContext),
                                          schemeType(portalAccessContext));
                              if (!errors.isEmpty()) {
                                return Mono.just(
                                    MutationResponse.failure(ApiStatus.VALIDATION_FAILED, errors));
                              }
                              var mapped = requestMapper.toCommand(body.applyToAllAccounts(), body);
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
                                  .map(responseMapper::toResponse);
                            })));
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

  private String resolveLanguage(ContextView contextView, String acceptLanguage) {
    RequestHeaderContext requestHeaderContext =
        contextView.getOrDefault(RequestHeaderContextKeys.CONTEXT_KEY, null);
    return RequestLanguageResolver.resolve(requestHeaderContext, acceptLanguage, null);
  }
}
