package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
import com.bct.ngtpa.apiservice.application.port.out.PortalAccessContextResolver;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class PersonalInformationControllerTest {

    @Test
    void resolvesPortalContextAndLanguageForPersonalInformation() {
        AtomicReference<GetPersonalInformationCommand> captured = new AtomicReference<>();
        var result = new PersonalInformationResult(Map.of("addr1", "ABC Street"), Map.of("addr1", "EDITABLE_COM"));
        GetPersonalInformationUseCase useCase = command -> {
            captured.set(command);
            return Mono.just(result);
        };
        PersonalInformationWebMapper mapper = mock(PersonalInformationWebMapper.class);
        when(mapper.toFormPageResponse(eq(result), eq("zh_HK"), eq("JP"), eq("JPM"), eq("OE")))
                .thenReturn(response("zh_HK", "個人資料"));

        FormPageResponse<FormSchemaResponse> response = new PersonalInformationController(useCase, mapper, resolver(context("ACC-123")))
                .getPersonalInformation("en")
                .contextWrite(ctx -> ctx.put(RequestHeaderContextKeys.CONTEXT_KEY,
                        new RequestHeaderContext("ACC-123", "req-1", "zh-HK")))
                .block();

        assertEquals(true, response.success());
        assertEquals(ApiStatus.SUCCESS, response.status());
        assertEquals("personalInformationPage", response.page().id());
        assertEquals("個人資料", response.page().title());
        assertEquals("zh_HK", response.page().lang());
        assertEquals("personalInformationForm", response.form().id());
        assertTrue(response.messages().isEmpty());
        assertTrue(response.errors().isEmpty());
        assertEquals("zh_HK", captured.get().language());
    }

    @Test
    void prefersContextLanguageOverRawAcceptLanguageHeader() {
        AtomicReference<GetPersonalInformationCommand> captured = new AtomicReference<>();
        var result = new PersonalInformationResult(Map.of(), Map.of());
        GetPersonalInformationUseCase useCase = command -> {
            captured.set(command);
            return Mono.just(result);
        };
        PersonalInformationWebMapper mapper = mock(PersonalInformationWebMapper.class);
        when(mapper.toFormPageResponse(eq(result), eq("en"), eq("JP"), eq("JPM"), eq("OE")))
                .thenReturn(response("en", "Personal Information"));

        new PersonalInformationController(useCase, mapper, resolver(context("ACC-123")))
                .getPersonalInformation("zh-HK")
                .contextWrite(ctx -> ctx.put(RequestHeaderContextKeys.CONTEXT_KEY,
                        new RequestHeaderContext("ACC-123", "req-1", "en")))
                .block();

        assertEquals("en", captured.get().language());
    }

    @Test
    void rejectsMissingAccountRefAsInvalidMemberContext() {
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

    private FormPageResponse<FormSchemaResponse> response(String language, String title) {
        return FormPageResponse.success(
                new PageResponse("personalInformationPage", title, language),
                new FormSchemaResponse("personalInformationForm", "1.0", "view", null, null, null, null));
    }

    private static PortalAccessContextResolver resolver(PortalAccessContext context) {
        return new PortalAccessContextResolver() {
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
