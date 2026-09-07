package com.bct.ngtpa.apiservice.application.service.accountgainloss;

import com.bct.ngtpa.apiservice.application.dto.accountgainloss.*;
import com.bct.ngtpa.apiservice.application.dto.metadata.*;
import com.bct.ngtpa.apiservice.application.exception.AccountGainLossRetrievalException;
import com.bct.ngtpa.apiservice.application.service.presentation.DynamicDescriptionResolver;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;

public final class AccountGainLossAssembler {
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final GeneralMetadataKey NAV_PRECISION =
      new GeneralMetadataKey(GeneralMetadataName.NET_ASSET_VALUE, GeneralMetadataType.PRECISION);
  private final AccountGainLossPrecisionRenderer renderer = new AccountGainLossPrecisionRenderer();
  private final DynamicDescriptionResolver descriptions = new DynamicDescriptionResolver();

  public AccountGainLossResult assemble(
      GetAccountGainLossCommand command,
      ResolvedGeneralMetadataSet metadata,
      RawAccountGainLossData raw) {
    try {
      if (command == null
          || metadata == null
          || raw == null
          || raw.priceDate() == null
          || raw.funds() == null) throw new IllegalArgumentException();
      var precision =
          GeneralMetadataPrecision.parse(metadata.find(NAV_PRECISION).orElseThrow().value())
              .value();
      var ordered =
          raw.funds().stream()
              .sorted(
                  Comparator.comparing(RawFundGainLoss::fundSequence)
                      .thenComparing(f -> f.fundCode().trim()))
              .toList();
      BigDecimal total =
          ordered.stream().map(RawFundGainLoss::gainLoss).reduce(BigDecimal.ZERO, BigDecimal::add);
      var funds =
          ordered.stream()
              .map(
                  f ->
                      new AccountGainLossFund(
                          f.fundCode().trim(),
                          descriptions.resolve(
                              command.language(), f.fundName(), f.chineseFundName()),
                          f.fundSequence(),
                          renderer.render(f.gainLoss(), precision)))
              .toList();
      return new AccountGainLossResult(
          command.period(), raw.priceDate().format(DATE), renderer.render(total, precision), funds);
    } catch (AccountGainLossRetrievalException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw AccountGainLossRetrievalException.systemFailure();
    }
  }
}
