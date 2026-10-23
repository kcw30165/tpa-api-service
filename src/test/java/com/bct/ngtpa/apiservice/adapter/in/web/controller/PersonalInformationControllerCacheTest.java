package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedSessionCookieReader;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateResponseMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.personalinformation.PersonalInformationController;
import com.bct.ngtpa.apiservice.adapter.in.web.response.FormPageResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.FormSchemaResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.PageResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.validation.PersonalInformationUpdateYamlValidator;
import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.GetPersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.dto.PersonalInformationResult;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
import com.bct.ngtpa.apiservice.application.port.in.GetPersonalInformationUseCase;
import com.bct.ngtpa.apiservice.application.port.in.UpdatePersonalInformationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

/** Sub-task 3.3: the GET controller hands session, account and request id over to the use case. */
class PersonalInformationControllerCacheTest {

  private static final AuthenticatedSessionId SID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");
  private static final GetPersonalInformationCommand COMMAND =
      new GetPersonalInformationCommand(
          "en", "JP", "policy-1", "cert-1", "user-1", ActorUserRole.ROLE_USER_MBR);

  private final GetPersonalInformationUseCase useCase = mock(GetPersonalInformationUseCase.class);
  private final PersonalInformationWebMapper mapper = mock(PersonalInformationWebMapper.class);
  private final PersonalInformationResult result =
      new PersonalInformationResult(Map.of("addr1", "ABC Street"), Map.of("addr1", "EDITABLE_COM"));

  private void useCaseReturnsTheResult() {
    when(useCase.execute(any())).thenReturn(Mono.just(result));
    when(useCase.executeAndCache(any(), any(), any(), any())).thenReturn(Mono.just(result));
    when(mapper.toFormPageResponse(eq(result), eq("en"), eq("JP"), eq("JPM"), eq("OE")))
        .thenReturn(formPage());
  }

  private static FormPageResponse<FormSchemaResponse> formPage() {
    return FormPageResponse.success(
        new PageResponse("personalInformationPage", "Personal Information", "en"),
        new FormSchemaResponse("personalInformationForm", "1.0", "view", null, null, null, null));
  }

  private PersonalInformationController controller(
      String accountRef, AuthenticatedSessionCookieReader reader) {
    return new PersonalInformationController(
        useCase,
        mapper,
        resolver(context(accountRef)),
        mock(UpdatePersonalInformationUseCase.class),
        mock(PersonalInformationUpdateWebMapper.class),
        mock(PersonalInformationUpdateResponseMapper.class),
        mock(PersonalInformationUpdateYamlValidator.class),
        reader,
        PersonalInformationAccessCheckerTestSupport.passing());
  }

  @Test
  void passesTheSessionIdAccountRefAndRequestIdToTheUseCase() {
    useCaseReturnsTheResult();

    var response =
        controller("ACC-123", exchange -> Optional.of(SID))
            .getPersonalInformation("en", exchange())
            .contextWrite(
                ctx ->
                    ctx.put(
                        RequestHeaderContextKeys.CONTEXT_KEY,
                        new RequestHeaderContext("ACC-123", "req-9", "en")))
            .block();

    assertThat(response).isNotNull();
    assertThat(response.success()).isTrue();
    verify(useCase).executeAndCache(COMMAND, Optional.of(SID), "ACC-123", "req-9");
  }

  @Test
  void passesAnEmptySessionWhenTheCookieIsMissing() {
    useCaseReturnsTheResult();

    var response =
        controller("ACC-123", exchange -> Optional.empty())
            .getPersonalInformation("en", exchange())
            .contextWrite(
                ctx ->
                    ctx.put(
                        RequestHeaderContextKeys.CONTEXT_KEY,
                        new RequestHeaderContext("ACC-123", null, "en")))
            .block();

    assertThat(response).isNotNull();
    verify(useCase).executeAndCache(eq(COMMAND), eq(Optional.empty()), eq("ACC-123"), isNull());
  }

  @Test
  void aMissingAccountRefIsRejectedBeforeTheUseCaseIsCalled() {
    var controller = controller(" ", exchange -> Optional.of(SID));

    var exception =
        assertThrows(
            PortalAccessContextResolutionException.class,
            () ->
                controller
                    .getPersonalInformation("en", exchange())
                    .contextWrite(
                        ctx ->
                            ctx.put(
                                RequestHeaderContextKeys.CONTEXT_KEY,
                                new RequestHeaderContext(" ", "req-1", "en")))
                    .block());

    assertThat(exception).isNotNull();
    verifyNoInteractions(useCase);
  }

  private static MockServerWebExchange exchange() {
    return MockServerWebExchange.from(
        MockServerHttpRequest.get("/api/v1/personal-information").build());
  }

  private static CurrentPortalAccessContextResolver resolver(PortalAccessContext context) {
    return new CurrentPortalAccessContextResolver() {
      @Override
      public Mono<PortalAccessContext> current() {
        return Mono.just(context);
      }

      @Override
      public Mono<PortalAccessContext> currentOrEmpty() {
        return Mono.just(context);
      }
    };
  }

  private static PortalAccessContext context(String accountRef) {
    return new PortalAccessContext(
        new ActorContext("user-1", ActorUserRole.ROLE_USER_MBR),
        new AccountContext(
            accountRef, "JP", "policy-1", "cert-1", "JPM", "OE", TermStatus.BLANK, null));
  }
}
