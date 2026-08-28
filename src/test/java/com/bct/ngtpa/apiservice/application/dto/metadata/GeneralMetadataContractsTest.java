package com.bct.ngtpa.apiservice.application.dto.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GeneralMetadataContractsTest {
  @Test
  void mapsNamesInBothDirections() {
    assertThat(GeneralMetadataWireValues.nameToWire(GeneralMetadataName.FUND_PRICE))
        .isEqualTo("fund-pri");
    assertThat(GeneralMetadataWireValues.nameToWire(GeneralMetadataName.NET_ASSET_VALUE))
        .isEqualTo("nav");
    assertThat(GeneralMetadataWireValues.nameToWire(GeneralMetadataName.UNIT_BALANCE))
        .isEqualTo("unit");
    assertThat(GeneralMetadataWireValues.nameToWire(GeneralMetadataName.FX_RATE))
        .isEqualTo("fx-rate");
    assertThat(GeneralMetadataWireValues.nameFromWire("fund-pri"))
        .isEqualTo(GeneralMetadataName.FUND_PRICE);
    assertThat(GeneralMetadataWireValues.nameFromWire("nav"))
        .isEqualTo(GeneralMetadataName.NET_ASSET_VALUE);
    assertThat(GeneralMetadataWireValues.nameFromWire("unit"))
        .isEqualTo(GeneralMetadataName.UNIT_BALANCE);
    assertThat(GeneralMetadataWireValues.nameFromWire("fx-rate"))
        .isEqualTo(GeneralMetadataName.FX_RATE);
  }

  @Test
  void mapsTypesInBothDirections() {
    assertThat(GeneralMetadataWireValues.typeToWire(GeneralMetadataType.PRECISION))
        .isEqualTo("precision");
    assertThat(GeneralMetadataWireValues.typeToWire(GeneralMetadataType.ROUNDING_METHOD))
        .isEqualTo("round-method");
    assertThat(GeneralMetadataWireValues.typeFromWire("precision"))
        .isEqualTo(GeneralMetadataType.PRECISION);
    assertThat(GeneralMetadataWireValues.typeFromWire("round-method"))
        .isEqualTo(GeneralMetadataType.ROUNDING_METHOD);
  }

  @Test
  void rejectsUnknownWireValues() {
    assertThatThrownBy(() -> GeneralMetadataWireValues.nameFromWire("other"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unknown general metadata name wire value");
    assertThatThrownBy(() -> GeneralMetadataWireValues.typeFromWire("other"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unknown general metadata type wire value");
  }

  @Test
  void enforcesScopeAndKeyInvariantsWithoutEchoingValues() {
    assertThatThrownBy(() -> new GeneralMetadataScope("", "trust", "policy"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("accountEnv must not be blank");
    assertThatThrownBy(() -> new GeneralMetadataScope("env", "", "policy"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("trustCode must not be blank");
    assertThatThrownBy(() -> new GeneralMetadataScope("env", "trust", ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("policyNo must not be blank");
    assertThatThrownBy(() -> new GeneralMetadataKey(null, GeneralMetadataType.PRECISION))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void exposesBothResolutionSources() {
    assertThat(GeneralMetadataResolutionSource.values())
        .containsExactly(
            GeneralMetadataResolutionSource.DEFAULT_ALL,
            GeneralMetadataResolutionSource.POLICY_SPECIFIC);
  }

  @Test
  void rejectsDuplicateKeysAtNormalizationSeam() {
    var key = new GeneralMetadataKey(GeneralMetadataName.FUND_PRICE, GeneralMetadataType.PRECISION);
    var first = new ResolvedGeneralMetadata(key, "2");
    var conflicting = new ResolvedGeneralMetadata(key, "3");
    assertThatThrownBy(
            () ->
                ResolvedGeneralMetadataSet.normalize(
                    new GeneralMetadataScope("env", "trust", "policy"),
                    GeneralMetadataResolutionSource.POLICY_SPECIFIC,
                    List.of(first, conflicting)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Conflicting duplicate general metadata key");
    assertThat(
            ResolvedGeneralMetadataSet.normalize(
                    new GeneralMetadataScope("env", "trust", "policy"),
                    GeneralMetadataResolutionSource.POLICY_SPECIFIC,
                    List.of(first, first))
                .entries())
        .containsExactly(first);
  }

  @Test
  void parsesPrecisionWithoutArbitraryMaximum() {
    assertThat(GeneralMetadataPrecision.parse("0").value()).isEqualTo(BigInteger.ZERO);
    assertThat(GeneralMetadataPrecision.parse("12").value()).isEqualTo(BigInteger.valueOf(12));
    assertThat(GeneralMetadataPrecision.parse("999999999999999999999999999999").value())
        .isEqualTo(new BigInteger("999999999999999999999999999999"));
    for (String invalid : List.of("-1", "1.5", "", " ", "abc")) {
      assertThatThrownBy(() -> GeneralMetadataPrecision.parse(invalid))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Invalid general metadata precision");
    }
  }

  @Test
  void resolverQuerySupportsASetOfRequiredKeys() {
    var key =
        new GeneralMetadataKey(GeneralMetadataName.UNIT_BALANCE, GeneralMetadataType.PRECISION);
    var query =
        new ResolveGeneralMetadataQuery(
            new GeneralMetadataScope("env", "trust", "policy"), Set.of(key));
    assertThat(query.requiredKeys()).containsExactly(key);
  }

  @Test
  void phaseIi001PreservesTrustedActorRole() {
    for (ActorUserRole role : ActorUserRole.values()) {
      var command =
          new FetchGeneralMetadataCommand(
              new GeneralMetadataScope("env", "trust", "policy"), Set.of(), role);
      assertThat(command.actorUserRole()).isSameAs(role);
    }
    assertThatThrownBy(
            () ->
                new FetchGeneralMetadataCommand(
                    new GeneralMetadataScope("env", "trust", "policy"), Set.of(), null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("actorUserRole must not be null");
  }
}
