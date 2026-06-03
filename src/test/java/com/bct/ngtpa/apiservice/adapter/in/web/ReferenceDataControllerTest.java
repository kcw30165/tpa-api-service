package com.bct.ngtpa.apiservice.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bct.ngtpa.apiservice.application.dto.GetReferenceDataCountriesCommand;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDataCountriesResult;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
import com.bct.ngtpa.apiservice.application.port.in.GetReferenceDataCountriesUseCase;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class ReferenceDataControllerTest {

    @Test
    void resolvesAccountRefAndNormalizedLanguage() {
        AtomicReference<GetReferenceDataCountriesCommand> captured = new AtomicReference<>();
        ReferenceDataCountriesResult expected = new ReferenceDataCountriesResult(List.of(), List.of());
        GetReferenceDataCountriesUseCase useCase = command -> {
            captured.set(command);
            return Mono.just(expected);
        };

        ReferenceDataCountriesResult actual = new ReferenceDataController(useCase)
                .getCountries("en")
                .contextWrite(ctx -> ctx.put(RequestHeaderContextKeys.CONTEXT_KEY,
                        new RequestHeaderContext("ACC-789", "req-1", "zh-HK")))
                .block();

        assertEquals(expected, actual);
        assertEquals("ACC-789", captured.get().accountRef());
        assertEquals("zh_HK", captured.get().language());
    }

    @Test
    void rejectsMissingRequestHeaderContext() {
        GetReferenceDataCountriesUseCase useCase = command -> Mono.just(new ReferenceDataCountriesResult(List.of(), List.of()));

        PortalAccessContextResolutionException ex = assertThrows(
                PortalAccessContextResolutionException.class,
                () -> new ReferenceDataController(useCase).getCountries("en").block());

        assertEquals(ErrorCodes.MEMBER_CONTEXT_INVALID, ex.getErrorCode());
    }
}
