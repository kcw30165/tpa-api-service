package com.bct.ngtpa.apiservice.application.service.accountbalance;

import com.bct.ngtpa.apiservice.application.dto.accountbalance.presentation.*;
import com.bct.ngtpa.apiservice.application.dto.metadata.*;
import com.bct.ngtpa.apiservice.application.exception.AccountBalanceRetrievalException;
import com.bct.ngtpa.apiservice.application.port.in.AccountBalancePresentationTransformer;
import java.math.BigInteger;
import java.util.*;

public final class DefaultAccountBalancePresentationTransformer
    implements AccountBalancePresentationTransformer {
  @Override
  public PresentedAccountBalanceData transform(
      String language, ResolvedGeneralMetadataSet metadata) {
    precisions(metadata);
    return new PresentedAccountBalanceData(Map.of(), Map.of());
  }

  /**
   * Precision metadata keys required by Account Balance rendering.
   *
   * <p>This set is deliberately explicit rather than derived from every enum constant. Resolving
   * the whole enum would make Account Balance fail as soon as a new metadata name is introduced for
   * another feature.
   */
  private static final Set<GeneralMetadataName> ACCOUNT_BALANCE_PRECISIONS =
      Set.of(
          GeneralMetadataName.FUND_PRICE,
          GeneralMetadataName.NET_ASSET_VALUE,
          GeneralMetadataName.UNIT_BALANCE,
          GeneralMetadataName.FX_RATE);

  public Map<GeneralMetadataName, BigInteger> precisions(ResolvedGeneralMetadataSet metadata) {
    return precisions(metadata, ACCOUNT_BALANCE_PRECISIONS);
  }

  public Map<GeneralMetadataName, BigInteger> precisions(
      ResolvedGeneralMetadataSet metadata, Set<GeneralMetadataName> names) {
    try {
      if (metadata == null || names == null || names.isEmpty())
        throw new IllegalArgumentException();
      var result = new EnumMap<GeneralMetadataName, BigInteger>(GeneralMetadataName.class);
      for (var name : names) {
        var key = new GeneralMetadataKey(name, GeneralMetadataType.PRECISION);
        String raw = metadata.find(key).orElseThrow().value();
        result.put(name, GeneralMetadataPrecision.parse(raw).value());
      }
      return Map.copyOf(result);
    } catch (RuntimeException ex) {
      throw AccountBalanceRetrievalException.systemFailure();
    }
  }
}
