package com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.mapper.FundComparisonPricesWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.request.FundComparisonPricesRequest;
import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FetchFundComparisonPricesUseCaseCommand;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FundComparisonPricesResult;
import com.bct.ngtpa.apiservice.application.dto.fundprice.RawFundPriceData;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.application.usecase.fundcomparison.FundComparisonPricesUseCase;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

/** Fund Comparison M5.4: the prices controller hands session, account and request id over. */
class FundComparisonPricesControllerSelectionTest {

  private static final AuthenticatedSessionId SID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");
  private static final FundComparisonPricesWebMapper MAPPER =
      new FundComparisonPricesWebMapper(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

  private final FundComparisonPricesUseCase useCase = mock(FundComparisonPricesUseCase.class);

  private static PortalAccessContext context() {
    return new PortalAccessContext(
        new ActorContext("U", ActorUserRole.ROLE_USER_MBR),
        new AccountContext(
            "ACC-1", "DB", "00000008802", "0230", "T", "OE", TermStatus.BLANK, null));
  }

  private static CurrentPortalAccessContextResolver resolver() {
    return new CurrentPortalAccessContextResolver() {
      @Override
      public Mono<PortalAccessContext> current() {
        return Mono.just(context());
      }

      @Override
      public Mono<PortalAccessContext> currentOrEmpty() {
        return Mono.just(context());
      }
    };
  }

  private static MockServerWebExchange exchange() {
    return MockServerWebExchange.from(
        MockServerHttpRequest.post("/api/v1/fund-comparison/prices").build());
  }

  private void useCaseReturnsPrices() {
    when(useCase.getPrices(any(), any(), any(), any()))
        .thenReturn(
            Mono.just(
                new FundComparisonPricesResult(
                    LocalDate.of(2026, 10, 6),
                    LocalDate.of(2026, 10, 15),
                    List.of("FAEU"),
                    new RawFundPriceData(List.of()))));
  }

  private static FetchFundComparisonPricesUseCaseCommand expectedCommand() {
    return new FetchFundComparisonPricesUseCaseCommand(
        "00000008802", "DB", ActorUserRole.ROLE_USER_MBR, List.of("faeu"), "10d");
  }

  @Test
  void passesTheValidatedSelectionSessionIdAccountRefAndRequestId() {
    useCaseReturnsPrices();
    var controller =
        new FundComparisonPricesController(
            useCase, resolver(), MAPPER, exchange -> Optional.of(SID));

    var response =
        controller
            .getPrices(new FundComparisonPricesRequest(List.of(" faeu "), " 10d "), exchange())
            .contextWrite(
                ctx ->
                    ctx.put(
                        RequestHeaderContextKeys.CONTEXT_KEY,
                        new RequestHeaderContext("ACC-1", "req-9", "en")))
            .block();

    assertThat(response).isNotNull();
    verify(useCase)
        .getPrices(eq(expectedCommand()), eq(Optional.of(SID)), eq("ACC-1"), eq("req-9"));
  }

  @Test
  void passesAnEmptySessionWhenTheCookieIsMissing() {
    useCaseReturnsPrices();
    var controller =
        new FundComparisonPricesController(
            useCase, resolver(), MAPPER, exchange -> Optional.empty());

    var response =
        controller
            .getPrices(new FundComparisonPricesRequest(List.of("faeu"), "10d"), exchange())
            .block();

    assertThat(response).isNotNull();
    verify(useCase).getPrices(eq(expectedCommand()), eq(Optional.empty()), eq("ACC-1"), isNull());
  }

  @Test
  void requiresTheSessionCookieReader() {
    var resolver = resolver();

    assertThatThrownBy(() -> new FundComparisonPricesController(useCase, resolver, MAPPER, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("sessionCookieReader");
  }
}
