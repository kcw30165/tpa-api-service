package com.bct.ngtpa.apiservice.adapter.in.web.validation;

import com.bct.ngtpa.apiservice.adapter.in.web.request.UpdatePersonalInformationRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.application.dto.GetPersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.usecase.personalinformation.PersonalInformationAccessConfigService;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Decides whether the fields submitted by a PUT may be edited, from the field-access config of the
 * selected account (design A.3, D-13, D-14).
 *
 * <p>The config comes from {@link PersonalInformationAccessConfigService#resolve}: the session
 * cache, else one TRPGetMemberInfo call. When a rejection was decided from a cached entry, the
 * config is fetched once more ({@link PersonalInformationAccessConfigService#refresh}) and the
 * request is revalidated, so a stale cache can never block a member. A rejection decided from a
 * freshly fetched config is final.
 *
 * <p>A request without submitted fields has nothing to check and triggers no lookup. A failed
 * lookup or refetch is propagated unchanged to the caller. Errors carry codes and field targets
 * only (D-14).
 */
@Component
public class PersonalInformationFieldAccessChecker {

  private final PersonalInformationAccessConfigService configService;
  private final PersonalInformationFieldAccessValidator validator;

  public PersonalInformationFieldAccessChecker(
      PersonalInformationAccessConfigService configService,
      PersonalInformationFieldAccessValidator validator) {
    this.configService = Objects.requireNonNull(configService, "configService must not be null");
    this.validator = Objects.requireNonNull(validator, "validator must not be null");
  }

  public Mono<List<ApiError>> check(
      UpdatePersonalInformationRequest request,
      GetPersonalInformationCommand command,
      Optional<AuthenticatedSessionId> sessionId,
      String accountRef,
      String requestId) {
    Objects.requireNonNull(command, "command must not be null");
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    if (request == null || request.fields() == null || request.fields().isEmpty()) {
      return Mono.just(List.of());
    }
    return configService
        .resolve(command, sessionId, accountRef, requestId)
        .flatMap(
            resolved -> {
              List<ApiError> errors = validator.validate(request, resolved.itemConfig());
              if (errors.isEmpty() || !resolved.fromCache()) {
                return Mono.just(errors);
              }
              return configService
                  .refresh(command, sessionId, accountRef, requestId)
                  .map(fresh -> validator.validate(request, fresh));
            });
  }
}
