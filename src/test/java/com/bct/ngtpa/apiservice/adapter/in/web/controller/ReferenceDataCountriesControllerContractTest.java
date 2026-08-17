package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bct.ngtpa.apiservice.application.dto.*;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
import com.bct.ngtpa.apiservice.application.port.in.GetReferenceDataCountriesUseCase;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.shared.web.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class ReferenceDataCountriesControllerContractTest {
    @Test void passesTrustedContextAndNormalizedLanguage() {
        AtomicReference<GetReferenceDataCountriesCommand> captured=new AtomicReference<>();
        GetReferenceDataCountriesUseCase useCase=c->{captured.set(c);return Mono.just(result());};
        var body=new ReferenceDataController(useCase,resolver(context("JP",ActorUserRole.ROLE_USER_EPR)))
                .getCountries("en").contextWrite(c->c.put(RequestHeaderContextKeys.CONTEXT_KEY,
                        new RequestHeaderContext("ACC-123","req-1","zh-HK"))).block();
        assertEquals("zh_HK",captured.get().language());
        assertEquals("JP",captured.get().accountEnv());
        assertEquals(ActorUserRole.ROLE_USER_EPR,captured.get().actorUserRole());
        assertEquals("HK",body.countries().getFirst().value());
    }
    @Test void missingAuthenticatedContextFailsThroughResolver() {
        CurrentPortalAccessContextResolver missing=new CurrentPortalAccessContextResolver(){
            public Mono<PortalAccessContext> current(){return Mono.error(new PortalAccessContextResolutionException("err.member.context.invalid","missing"));}
            public Mono<PortalAccessContext> currentOrEmpty(){return Mono.empty();}};
        assertThrows(PortalAccessContextResolutionException.class,()->new ReferenceDataController(c->Mono.just(result()),missing).getCountries("en").block());
    }
    private static CurrentPortalAccessContextResolver resolver(PortalAccessContext ctx){return new CurrentPortalAccessContextResolver(){public Mono<PortalAccessContext> current(){return Mono.just(ctx);}public Mono<PortalAccessContext> currentOrEmpty(){return Mono.just(ctx);}};}
    private static PortalAccessContext context(String env,ActorUserRole role){return new PortalAccessContext(new ActorContext("USER-1",role),new AccountContext("ACC-123",env,"POL-1","CERT-1","TRUST","SCHEME",TermStatus.BLANK,null));}
    private static ReferenceDataCountriesResult result(){return new ReferenceDataCountriesResult(List.of(new ReferenceDataOption("HK","Hong Kong")),List.of(new ReferenceDataOption("+852","Hong Kong")));}
}
