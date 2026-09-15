package com.bct.ngtpa.apiservice.adapter.out.apim.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.shared.web.PortalAccessContextKeys;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

class ApimContextPropagationExchangeFilterHeaderTest {

  private final ApimContextPropagationExchangeFilter filter =
      new ApimContextPropagationExchangeFilter();

  @Test
  void emitsEmptyTrustAndRoleWhenNoPortalAccessContext() {
    ClientRequest captured = run(Context.empty());
    assertEquals("", captured.headers().getFirst("X-Trust"));
    assertEquals("", captured.headers().getFirst("X-User-Role"));
  }

  @Test
  void emitsTrustAndRoleFromPortalAccessContext() {
    PortalAccessContext context =
        new PortalAccessContext(
            new ActorContext("actor-1", ActorUserRole.ROLE_USER_MBR),
            new AccountContext("acc-ref", "DEV", "policy", "cert", "TR-CODE", "scheme", null, null));
    ClientRequest captured = run(Context.of(PortalAccessContextKeys.CONTEXT_KEY, context));
    assertEquals("TR-CODE", captured.headers().getFirst("X-Trust"));
    assertEquals("ROLE_USER_MBR", captured.headers().getFirst("X-User-Role"));
  }

  @Test
  void propagatesRequestIdFromContext() {
    ClientRequest captured =
        run(Context.of(RequestCorrelation.REQUEST_ID_CONTEXT_KEY, "req-123"));
    assertEquals("req-123", captured.headers().getFirst(RequestCorrelation.REQUEST_ID_HEADER));
  }

  private ClientRequest run(Context context) {
    ExchangeFilterFunction exchangeFilter = filter.filter();
    AtomicReference<ClientRequest> captured = new AtomicReference<>();
    ExchangeFunction exchange =
        request -> {
          captured.set(request);
          return Mono.just(ClientResponse.create(HttpStatus.OK).build());
        };
    ClientRequest request =
        ClientRequest.create(HttpMethod.POST, URI.create("https://apim.example/test")).build();
    exchangeFilter.filter(request, exchange).contextWrite(context).block();
    return captured.get();
  }
}
