package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.FormPageResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.FormSchemaResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.PageResponse;
import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.GetPersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.dto.PersonalInformationResult;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
import com.bct.ngtpa.apiservice.application.port.in.GetPersonalInformationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class PersonalInformationControllerContractTest {

    @Test
    void getPersonalInformationReturns200WithGenericFormPageResponse() {
        var result = new PersonalInformationResult(Map.of(), Map.of());
        GetPersonalInformationUseCase useCase = command -> Mono.just(result);
        PersonalInformationWebMapper mapper = mock(PersonalInformationWebMapper.class);
        when(mapper.toFormPageResponse(eq(result), eq("en"), eq("JP"), eq("JPM"), eq("OE")))
                .thenReturn(response("en", "Personal Information"));

        var body = new PersonalInformationController(useCase, mapper, resolver(context("ACC-123")))
                .getPersonalInformation("en")
                .contextWrite(ctx -> ctx.put(RequestHeaderContextKeys.CONTEXT_KEY,
                        new RequestHeaderContext("ACC-123", "req-1", "en")))
                .block();

        assertEquals(true, body.success());
        assertEquals(ApiStatus.SUCCESS, body.status());
        assertEquals("personalInformationPage", body.page().id());
        assertEquals("personalInformationForm", body.form().id());
        assertTrue(body.errors().isEmpty());
    }

    @Test
    void getPersonalInformationPassesLanguageOnlyToUseCase() {
        AtomicReference<GetPersonalInformationCommand> captured = new AtomicReference<>();
        var result = new PersonalInformationResult(Map.of(), Map.of());
        GetPersonalInformationUseCase useCase = command -> {
            captured.set(command);
            return Mono.just(result);
        };
        PersonalInformationWebMapper mapper = mock(PersonalInformationWebMapper.class);
        when(mapper.toFormPageResponse(eq(result), eq("zh_HK"), eq("JP"), eq("JPM"), eq("OE")))
                .thenReturn(response("zh_HK", "個人資料"));

        new PersonalInformationController(useCase, mapper, resolver(context("ACC-123")))
                .getPersonalInformation("en")
                .contextWrite(ctx -> ctx.put(RequestHeaderContextKeys.CONTEXT_KEY,
                        new RequestHeaderContext("ACC-123", "req-1", "zh-HK")))
                .block();

        assertEquals("zh_HK", captured.get().language());
    }

    @Test
    void getPersonalInformationReturnsGenericBaseErrorWhenAccountRefMissing() {
        GetPersonalInformationUseCase useCase = command -> Mono.just(new PersonalInformationResult(Map.of(), Map.of()));
        PersonalInformationWebMapper mapper = mock(PersonalInformationWebMapper.class);

        PortalAccessContextResolutionException ex = assertThrows(
                PortalAccessContextResolutionException.class,
                () -> new PersonalInformationController(useCase, mapper, resolver(context(" ")))
                        .getPersonalInformation("en")
                        .contextWrite(ctx -> ctx.put(RequestHeaderContextKeys.CONTEXT_KEY,
                                new RequestHeaderContext(" ", "req-1", "en")))
                        .block());

        assertEquals(ErrorCodes.MEMBER_CONTEXT_INVALID, ex.getErrorCode());
    }

    @Test
    void getPersonalInformationDelegatesToWebMapperAfterUseCase() {
        var result = new PersonalInformationResult(Map.of("email", "a@b.test"), Map.of("email", "READONLY"));
        GetPersonalInformationUseCase useCase = command -> Mono.just(result);
        PersonalInformationWebMapper mapper = mock(PersonalInformationWebMapper.class);
        var expected = response("en", "Personal Information");
        when(mapper.toFormPageResponse(eq(result), eq("en"), eq("JP"), eq("JPM"), eq("OE")))
                .thenReturn(expected);

        var actual = new PersonalInformationController(useCase, mapper, resolver(context("ACC-123")))
                .getPersonalInformation("en")
                .contextWrite(ctx -> ctx.put(RequestHeaderContextKeys.CONTEXT_KEY,
                        new RequestHeaderContext("ACC-123", "req-1", "en")))
                .block();

        assertEquals(expected, actual);
        verify(mapper).toFormPageResponse(result, "en", "JP", "JPM", "OE");
    }

    private FormPageResponse<FormSchemaResponse> response(String language, String title) {
        return FormPageResponse.success(
                new PageResponse("personalInformationPage", title, language),
                new FormSchemaResponse("personalInformationForm", "1.0", "view", null, null, null, null));
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
                new ActorContext("user-1", "SELF"),
                new AccountContext(accountRef, "JP", "policy-1", "cert-1", "JPM", "OE", TermStatus.BLANK, null));
    }
}
