package com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.mapper.FundComparisonOptionsMapper;
import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FetchFundComparisonOptionsCommand;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.RawFundComparisonOptionsData;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.RawFundComparisonOptionsData.RawFundComparisonOption;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.application.usecase.fundcomparison.FundComparisonOptionsUseCase;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

/** Fund Comparison M5.3: the options controller hands session, account and request id over. */
class FundComparisonOptionsControllerCacheTest {

  private static final AuthenticatedSessionId SID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");

  private final FundComparisonOptionsUseCase useCase = mock(FundComparisonOptionsUseCase.class);

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
        MockServerHttpRequest.post("/api/v1/fund-comparison/options").build());
  }

  private void useCaseReturnsOptions() {
    when(useCase.getOptionsAndCache(any(), any(), any(), any()))
        .thenReturn(
            Mono.just(
                new RawFundComparisonOptionsData(
                    "10D", List.of(new RawFundComparisonOption("FAEU", "A Fund", "")))));
  }

  private static FetchFundComparisonOptionsCommand expectedCommand() {
    return new FetchFundComparisonOptionsCommand("00000008802", "DB", ActorUserRole.ROLE_USER_MBR);
  }

  @Test
  void passesTheSessionIdAccountRefAndRequestIdToTheUseCase() {
    useCaseReturnsOptions();
    var controller =
        new FundComparisonOptionsController(
            useCase, resolver(), new FundComparisonOptionsMapper(), exchange -> Optional.of(SID));

    var response =
        controller
            .getOptions(null, exchange())
            .contextWrite(
                ctx ->
                    ctx.put(
                        RequestHeaderContextKeys.CONTEXT_KEY,
                        new RequestHeaderContext("ACC-1", "req-9", "en")))
            .block();

    assertThat(response).isNotNull();
    verify(useCase).getOptionsAndCache(expectedCommand(), Optional.of(SID), "ACC-1", "req-9");
  }

  @Test
  void passesAnEmptySessionWhenTheCookieIsMissing() {
    useCaseReturnsOptions();
    var controller =
        new FundComparisonOptionsController(
            useCase, resolver(), new FundComparisonOptionsMapper(), exchange -> Optional.empty());

    var response = controller.getOptions(null, exchange()).block();

    assertThat(response).isNotNull();
    verify(useCase)
        .getOptionsAndCache(eq(expectedCommand()), eq(Optional.empty()), eq("ACC-1"), isNull());
  }

  @Test
  void requiresTheSessionCookieReader() {
    var resolver = resolver();
    var mapper = new FundComparisonOptionsMapper();

    assertThatThrownBy(() -> new FundComparisonOptionsController(useCase, resolver, mapper, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("sessionCookieReader");
  }
}
