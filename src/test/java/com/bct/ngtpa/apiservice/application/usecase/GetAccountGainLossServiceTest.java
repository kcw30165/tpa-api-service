package com.bct.ngtpa.apiservice.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.accountgainloss.AccountGainLossPeriod;
import com.bct.ngtpa.apiservice.application.dto.accountgainloss.AccountGainLossResult;
import com.bct.ngtpa.apiservice.application.dto.accountgainloss.GetAccountGainLossCommand;
import com.bct.ngtpa.apiservice.application.dto.accountgainloss.RawAccountGainLossData;
import com.bct.ngtpa.apiservice.application.dto.metadata.ResolvedGeneralMetadataSet;
import com.bct.ngtpa.apiservice.application.exception.AccountGainLossRetrievalException;
import com.bct.ngtpa.apiservice.application.port.in.GeneralMetadataResolver;
import com.bct.ngtpa.apiservice.application.port.out.AccountGainLossSourcePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.application.service.accountgainloss.AccountGainLossAssembler;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Branch-oriented reactive tests for {@link GetAccountGainLossService}. */
class GetAccountGainLossServiceTest {

  private final ReferenceDatePort referenceDatePort = mock(ReferenceDatePort.class);
  private final GeneralMetadataResolver metadataResolver = mock(GeneralMetadataResolver.class);
  private final AccountGainLossSourcePort source = mock(AccountGainLossSourcePort.class);
  private final AccountGainLossAssembler assembler = mock(AccountGainLossAssembler.class);

  private GetAccountGainLossService newService() {
    return new GetAccountGainLossService(referenceDatePort, metadataResolver, source, assembler);
  }

  private GetAccountGainLossCommand command() {
    return new GetAccountGainLossCommand(
        "en", AccountGainLossPeriod.YEAR_TO_DATE, "env1", "policy1", "cert1", "trust1",
        "SCHEME_A", "actor1", ActorUserRole.ROLE_USER_MBR);
  }

  @Test
  void getSucceedsThroughFullChain() {
    LocalDate refDate = LocalDate.of(2026, 1, 1);
    when(referenceDatePort.resolveReferenceDate(any())).thenReturn(Mono.just(refDate));
    ResolvedGeneralMetadataSet metadata = mock(ResolvedGeneralMetadataSet.class);
    when(metadataResolver.resolve(any())).thenReturn(Mono.just(metadata));
    RawAccountGainLossData raw = mock(RawAccountGainLossData.class);
    when(source.fetch(any())).thenReturn(Mono.just(raw));
    AccountGainLossResult expected =
        new AccountGainLossResult(AccountGainLossPeriod.YEAR_TO_DATE, "01/01/2026", "0.00", List.of());
    when(assembler.assemble(any(), any(), any())).thenReturn(expected);
    StepVerifier.create(newService().get(command())).expectNext(expected).verifyComplete();
  }

  @Test
  void referenceDateErrorMapsToSystemFailure() {
    when(referenceDatePort.resolveReferenceDate(any()))
        .thenReturn(Mono.error(new IllegalStateException("boom")));
    StepVerifier.create(newService().get(command()))
        .expectErrorSatisfies(
            ex -> {
              assertThat(ex).isInstanceOf(AccountGainLossRetrievalException.class);
              assertThat(((AccountGainLossRetrievalException) ex).reason())
                  .isEqualTo(AccountGainLossRetrievalException.Reason.SYSTEM_FAILURE);
            })
        .verify();
  }

  @Test
  void metadataResolutionErrorMapsToSystemFailure() {
    when(referenceDatePort.resolveReferenceDate(any())).thenReturn(Mono.just(LocalDate.of(2026, 1, 1)));
    when(metadataResolver.resolve(any())).thenReturn(Mono.error(new IllegalStateException("boom")));
    StepVerifier.create(newService().get(command()))
        .expectError(AccountGainLossRetrievalException.class)
        .verify();
  }

  @Test
  void sourceFetchErrorMapsToSystemFailure() {
    when(referenceDatePort.resolveReferenceDate(any())).thenReturn(Mono.just(LocalDate.of(2026, 1, 1)));
    when(metadataResolver.resolve(any())).thenReturn(Mono.just(mock(ResolvedGeneralMetadataSet.class)));
    when(source.fetch(any())).thenReturn(Mono.error(new IllegalStateException("boom")));
    StepVerifier.create(newService().get(command()))
        .expectError(AccountGainLossRetrievalException.class)
        .verify();
  }

  @Test
  void existingRetrievalExceptionPassesThroughUnchanged() {
    when(referenceDatePort.resolveReferenceDate(any()))
        .thenReturn(Mono.error(AccountGainLossRetrievalException.fundSwitchingInProgress()));
    StepVerifier.create(newService().get(command()))
        .expectErrorSatisfies(
            ex ->
                assertThat(((AccountGainLossRetrievalException) ex).reason())
                    .isEqualTo(AccountGainLossRetrievalException.Reason.FUND_SWITCHING_IN_PROGRESS))
        .verify();
  }
}
