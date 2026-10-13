package com.bct.ngtpa.apiservice.application.usecase.auth.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationEligibleTrustSet;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupAccount;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationSelectedTrust;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegistrationEligibleTrustSetTest {
  private static final List<RegistrationLookupAccount> ELIGIBLE =
      List.of(
          new RegistrationLookupAccount(
              "JP", "JPM", null, null, null, null, null, Boolean.FALSE, null),
          new RegistrationLookupAccount(
              "TB", "MSIG", null, null, null, null, null, Boolean.FALSE, null));

  @Test
  void authorizesEachExactEligiblePair() {
    String encoded = RegistrationEligibleTrustSet.encode(ELIGIBLE);

    assertThat(RegistrationEligibleTrustSet.contains(encoded, selected("JPM", "JP"))).isTrue();
    assertThat(RegistrationEligibleTrustSet.contains(encoded, selected("MSIG", "TB"))).isTrue();
  }

  @Test
  void rejectsReversedPair() {
    String encoded = RegistrationEligibleTrustSet.encode(ELIGIBLE);

    assertThat(RegistrationEligibleTrustSet.contains(encoded, selected("JP", "JPM"))).isFalse();
  }

  @Test
  void rejectsCrossPairsBuiltFromIndividuallyEligibleValues() {
    String encoded = RegistrationEligibleTrustSet.encode(ELIGIBLE);

    assertThat(RegistrationEligibleTrustSet.contains(encoded, selected("MSIG", "JP"))).isFalse();
    assertThat(RegistrationEligibleTrustSet.contains(encoded, selected("JPM", "TB"))).isFalse();
  }

  @Test
  void rejectsUnknownMissingAndBlankSelections() {
    String encoded = RegistrationEligibleTrustSet.encode(ELIGIBLE);

    assertThat(RegistrationEligibleTrustSet.contains(encoded, selected("UNKNOWN", "JP"))).isFalse();
    assertThat(RegistrationEligibleTrustSet.contains(encoded, selected("JPM", "UNKNOWN")))
        .isFalse();
    assertThat(RegistrationEligibleTrustSet.contains(null, selected("JPM", "JP"))).isFalse();
    assertThat(RegistrationEligibleTrustSet.contains("", selected("JPM", "JP"))).isFalse();
    assertThat(RegistrationEligibleTrustSet.contains(encoded, null)).isFalse();
  }

  @Test
  void encodingIsDeterministicAndDoesNotExposeRawTrustValues() {
    String first = RegistrationEligibleTrustSet.encode(ELIGIBLE);
    String second = RegistrationEligibleTrustSet.encode(List.of(ELIGIBLE.get(1), ELIGIBLE.get(0)));

    assertThat(first).isEqualTo(second).doesNotContain("JP", "JPM", "TB", "MSIG");
  }

  private static RegistrationSelectedTrust selected(String trustCode, String accountEnv) {
    return new RegistrationSelectedTrust(trustCode, accountEnv);
  }
}
