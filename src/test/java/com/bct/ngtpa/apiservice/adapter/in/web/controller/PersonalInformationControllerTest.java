package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationWebMapper;
import com.bct.ngtpa.apiservice.application.dto.GetPersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.dto.PersonalInformationResult;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
import com.bct.ngtpa.apiservice.application.port.in.GetPersonalInformationUseCase;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class PersonalInformationControllerTest {

    @Test
    void resolvesAccountRefAndLanguageFromRequestHeaderContext() {
        AtomicReference<GetPersonalInformationCommand> captured = new AtomicReference<>();
        var result = new PersonalInformationResult(Map.of("addr1", "ABC Street"), Map.of("addr1", "EDITABLE_COM"));
        GetPersonalInformationUseCase useCase = command -> {
            captured.set(command);
            return Mono.just(result);
        };
        PersonalInformationWebMapper mapper = mock(PersonalInformationWebMapper.class);
        when(mapper.toResponse(eq(result), eq("zh_HK"))).thenReturn(Map.of("ok", true));

        Map<String, Object> response = new PersonalInformationController(useCase, mapper)
                .getPersonalInformation("en")
                .contextWrite(ctx -> ctx.put(RequestHeaderContextKeys.CONTEXT_KEY,
                        new RequestHeaderContext("ACC-123", "req-1", "zh-HK")))
                .block();

        assertEquals(Map.of("ok", true), response);
        assertEquals("ACC-123", captured.get().accountRef());
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
        when(mapper.toResponse(eq(result), eq("en"))).thenReturn(Map.of());

        new PersonalInformationController(useCase, mapper)
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
                () -> new PersonalInformationController(useCase, mapper)
                        .getPersonalInformation("en")
                        .contextWrite(ctx -> ctx.put(RequestHeaderContextKeys.CONTEXT_KEY,
                                new RequestHeaderContext(" ", "req-1", "en")))
                        .block());

        assertEquals(ErrorCodes.MEMBER_CONTEXT_INVALID, ex.getErrorCode());
    }
}
