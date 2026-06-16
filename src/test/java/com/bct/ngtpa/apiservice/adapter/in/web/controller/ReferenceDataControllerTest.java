package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bct.ngtpa.apiservice.application.dto.GetReferenceDataCountriesCommand;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDataCountriesResult;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDataOption;
import com.bct.ngtpa.apiservice.application.port.in.GetReferenceDataCountriesUseCase;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class ReferenceDataControllerTest {

    @Test
    void passesLanguageOnlyCommandToUseCase() {
        AtomicReference<GetReferenceDataCountriesCommand> captured = new AtomicReference<>();
        GetReferenceDataCountriesUseCase useCase = command -> {
            captured.set(command);
            return Mono.just(result());
        };

        var result = new ReferenceDataController(useCase)
                .getCountries("en")
                .contextWrite(ctx -> ctx.put(RequestHeaderContextKeys.CONTEXT_KEY,
                        new RequestHeaderContext("ACC-123", "req-1", "zh-HK")))
                .block();

        assertEquals("zh_HK", captured.get().language());
        assertEquals("HK", result.countries().get(0).value());
    }

    @Test
    void doesNotRequireAccountRefWhenCountryListIsGlobal() {
        AtomicReference<GetReferenceDataCountriesCommand> captured = new AtomicReference<>();
        GetReferenceDataCountriesUseCase useCase = command -> {
            captured.set(command);
            return Mono.just(result());
        };

        var result = new ReferenceDataController(useCase)
                .getCountries("en")
                .block();

        assertEquals("en", captured.get().language());
        assertEquals("+852", result.callingCodes().get(0).value());
    }

    private ReferenceDataCountriesResult result() {
        return new ReferenceDataCountriesResult(
                List.of(new ReferenceDataOption("HK", "Hong Kong")),
                List.of(new ReferenceDataOption("+852", "Hong Kong")));
    }
}
