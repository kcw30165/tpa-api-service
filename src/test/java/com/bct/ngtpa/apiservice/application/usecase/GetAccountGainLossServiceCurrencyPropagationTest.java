package com.bct.ngtpa.apiservice.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.accountgainloss.AccountGainLossPeriod;
import com.bct.ngtpa.apiservice.application.dto.accountgainloss.FetchAccountGainLossCommand;
import com.bct.ngtpa.apiservice.application.dto.accountgainloss.GetAccountGainLossCommand;
import com.bct.ngtpa.apiservice.application.dto.accountgainloss.RawAccountGainLossData;
import com.bct.ngtpa.apiservice.application.dto.accountgainloss.RawFundGainLoss;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataKey;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataName;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataResolutionSource;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataScope;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataType;
import com.bct.ngtpa.apiservice.application.dto.metadata.ResolvedGeneralMetadata;
import com.bct.ngtpa.apiservice.application.dto.metadata.ResolvedGeneralMetadataSet;
import com.bct.ngtpa.apiservice.application.port.in.GeneralMetadataResolver;
import com.bct.ngtpa.apiservice.application.port.out.AccountGainLossSourcePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.application.service.accountgainloss.AccountGainLossAssembler;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Guards the currency-threading change to GetAccountGainLossService. The service previously had
 * no test coverage at all; these two cases protect exactly the behaviour being introduced.
 *
 * <p>Currency must be forwarded into {@code FetchAccountGainLossCommand} when the caller supplies
 * one, and must forward as {@code null} - not a fabricated {@code ""} - when the caller uses the
 * nine-argument compatibility constructor, which is the exact shape
 * {@code AccountGainLossController} still uses for the standalone, currency-less
 * {@code /api/v1/account/gain-loss} endpoint. Normalizing null to the wire empty string remains
 * the APIM adapter's responsibility, not this service's.
 */
class GetAccountGainLossServiceCurrencyPropagationTest {

  private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 8, 31);

  @Test
  void forwardsAnExplicitCurrencyToTheFetchCommand() {
    var captured = new AtomicReference<FetchAccountGainLossCommand>();
    var service = service(captured);

    var command =
        new GetAccountGainLossCommand(
            "en",
            AccountGainLossPeriod.SINCE_SCHEME_INCEPTION,
            "USD",
            "JP",
            "00000008802",
            "230",
            "T",
            "S",
            "U",
            ActorUserRole.ROLE_USER_MBR);

    StepVerifier.create(service.get(command)).expectNextCount(1).verifyComplete();

    assertThat(captured.get()).isNotNull();
    assertThat(captured.get().currency()).isEqualTo("USD");
  }

  @Test
  void compatibilityConstructorWithNoCurrencyForwardsNullRatherThanAnEmptyString() {
    var captured = new AtomicReference<FetchAccountGainLossCommand>();
    var service = service(captured);

    // Exactly the nine-argument shape AccountGainLossController uses today.
    var command =
        new GetAccountGainLossCommand(
            "en",
            AccountGainLossPeriod.YEAR_TO_DATE,
            "JP",
            "00000008802",
            "230",
            "T",
            "S",
            "U",
            ActorUserRole.ROLE_USER_MBR);

    StepVerifier.create(service.get(command)).expectNextCount(1).verifyComplete();

    assertThat(captured.get().currency()).isNull();
  }

  private GetAccountGainLossService service(
      AtomicReference<FetchAccountGainLossCommand> captured) {
    ReferenceDatePort referenceDatePort = accountEnv -> Mono.just(REFERENCE_DATE);
    GeneralMetadataResolver metadataResolver = query -> Mono.just(metadata());
    AccountGainLossSourcePort source =
        fetchCommand -> {
          captured.set(fetchCommand);
          return Mono.just(
              new RawAccountGainLossData(
                  REFERENCE_DATE,
                  List.of(
                      new RawFundGainLoss("DGLE", "Fund", "基金", 1, new BigDecimal("100.00")))));
        };
    var assembler = new AccountGainLossAssembler();
    return new GetAccountGainLossService(referenceDatePort, metadataResolver, source, assembler);
  }

  private static ResolvedGeneralMetadataSet metadata() {
    var scope = new GeneralMetadataScope("JP", "T", "P");
    return ResolvedGeneralMetadataSet.normalize(
        scope,
        GeneralMetadataResolutionSource.DEFAULT_ALL,
        List.of(
            new ResolvedGeneralMetadata(
                new GeneralMetadataKey(
                    GeneralMetadataName.NET_ASSET_VALUE, GeneralMetadataType.PRECISION),
                "2")));
  }
}
