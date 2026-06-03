package com.bct.ngtpa.apiservice.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bct.ngtpa.apiservice.application.dto.GetPersonalInformationCommand;
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
        GetPersonalInformationUseCase useCase = command -> {
            captured.set(command);
            return Mono.just(Map.of("ok", true));
        };

        Map<String, Object> response = new PersonalInformationController(useCase)
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
        GetPersonalInformationUseCase useCase = command -> {
            captured.set(command);
            return Mono.just(Map.of());
        };

        new PersonalInformationController(useCase)
                .getPersonalInformation("zh-HK")
                .contextWrite(ctx -> ctx.put(RequestHeaderContextKeys.CONTEXT_KEY,
                        new RequestHeaderContext("ACC-123", "req-1", "en")))
                .block();

        assertEquals("en", captured.get().language());
    }

    @Test
    void rejectsMissingAccountRefAsInvalidMemberContext() {
        GetPersonalInformationUseCase useCase = command -> Mono.just(Map.of());

        PortalAccessContextResolutionException ex = assertThrows(
                PortalAccessContextResolutionException.class,
                () -> new PersonalInformationController(useCase)
                        .getPersonalInformation("en")
                        .contextWrite(ctx -> ctx.put(RequestHeaderContextKeys.CONTEXT_KEY,
                                new RequestHeaderContext(" ", "req-1", "en")))
                        .block());

        assertEquals(ErrorCodes.MEMBER_CONTEXT_INVALID, ex.getErrorCode());
    }
}
