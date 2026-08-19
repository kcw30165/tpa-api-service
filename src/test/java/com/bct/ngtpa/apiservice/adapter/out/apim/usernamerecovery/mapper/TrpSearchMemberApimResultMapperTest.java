package com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.TrpSearchMemberApimDownstreamException;
import com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.dto.TrpSearchMemberApimDataItem;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryLookupResult;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrpSearchMemberApimResultMapperTest {
  private final TrpSearchMemberApimResultMapper mapper = new TrpSearchMemberApimResultMapper();

  @Test
  void memberNotFoundErrorMessageIsNotFoundResult() {
    var result = mapper.toResult("err.tpa.member.not.found", List.of());

    assertThat(result.status()).isEqualTo(UsernameRecoveryLookupResult.Status.NOT_FOUND);
    assertThat(result.candidates()).isEmpty();
  }

  @Test
  void blankOrMissingSuccessDataIsNotFoundResult() {
    assertThat(mapper.toResult("", List.of()).status())
        .isEqualTo(UsernameRecoveryLookupResult.Status.NOT_FOUND);
    assertThat(mapper.toResult("", null).status())
        .isEqualTo(UsernameRecoveryLookupResult.Status.NOT_FOUND);
    assertThat(mapper.toResult(null, List.of()).status())
        .isEqualTo(UsernameRecoveryLookupResult.Status.NOT_FOUND);
  }

  @Test
  void validRegisteredRecordMapsToMatchedCandidateWithProtectedUsername() {
    var result = mapper.toResult("", List.of(registeredItem("DB", "DBA", "db02")));

    assertThat(result.status()).isEqualTo(UsernameRecoveryLookupResult.Status.MATCHED);
    assertThat(result.candidates())
        .singleElement()
        .satisfies(
            candidate -> {
              assertThat(candidate.accountEnv()).isEqualTo("DB");
              assertThat(candidate.trustCode()).isEqualTo("DBA");
              assertThat(candidate.registered()).isTrue();
              assertThat(candidate.userName()).isEqualTo("db02");
            });
  }

  @Test
  void validUnregisteredRecordMapsToMatchedCandidateWithoutUsername() {
    var result = mapper.toResult("", List.of(unregisteredItem("OI", "JPM")));

    assertThat(result.status()).isEqualTo(UsernameRecoveryLookupResult.Status.MATCHED);
    assertThat(result.candidates())
        .singleElement()
        .satisfies(
            candidate -> {
              assertThat(candidate.accountEnv()).isEqualTo("OI");
              assertThat(candidate.trustCode()).isEqualTo("JPM");
              assertThat(candidate.registered()).isFalse();
              assertThat(candidate.userName()).isNull();
            });
  }

  @Test
  void multipleDistinctValidRecordsProduceMultipleCandidates() {
    var result =
        mapper.toResult(
            "", List.of(registeredItem("DB", "DBA", "db02"), unregisteredItem("OI", "JPM")));

    assertThat(result.status()).isEqualTo(UsernameRecoveryLookupResult.Status.MATCHED);
    assertThat(result.candidates()).hasSize(2);
  }

  @Test
  void registeredRecordWithoutCustIdIsContractInvalid() {
    assertThat(mapper.toResult("", List.of(registeredItem("DB", "DBA", ""))).status())
        .isEqualTo(UsernameRecoveryLookupResult.Status.CONTRACT_INVALID);
    assertThat(mapper.toResult("", List.of(registeredItem("DB", "DBA", null))).status())
        .isEqualTo(UsernameRecoveryLookupResult.Status.CONTRACT_INVALID);
  }

  @Test
  void unregisteredRecordWithCustIdIsContractInvalid() {
    assertThat(mapper.toResult("", List.of(item("OI", "JPM", false, "leaked"))).status())
        .isEqualTo(UsernameRecoveryLookupResult.Status.CONTRACT_INVALID);
  }

  @Test
  void missingOrBlankEnvOrTrustCodeIsContractInvalid() {
    assertThat(mapper.toResult("", List.of(item(null, "DBA", true, "db02"))).status())
        .isEqualTo(UsernameRecoveryLookupResult.Status.CONTRACT_INVALID);
    assertThat(mapper.toResult("", List.of(item("DB", null, true, "db02"))).status())
        .isEqualTo(UsernameRecoveryLookupResult.Status.CONTRACT_INVALID);
    assertThat(mapper.toResult("", List.of(item(" ", "DBA", true, "db02"))).status())
        .isEqualTo(UsernameRecoveryLookupResult.Status.CONTRACT_INVALID);
  }

  @Test
  void missingRegistrationFlagIsContractInvalid() {
    assertThat(mapper.toResult("", List.of(item("DB", "DBA", null, "db02"))).status())
        .isEqualTo(UsernameRecoveryLookupResult.Status.CONTRACT_INVALID);
  }

  @Test
  void duplicateEnvAndTrustCodeIsContractInvalid() {
    var result =
        mapper.toResult(
            "", List.of(registeredItem("DB", "DBA", "db02"), unregisteredItem("DB", "DBA")));

    assertThat(result.status()).isEqualTo(UsernameRecoveryLookupResult.Status.CONTRACT_INVALID);
  }

  @Test
  void invalidNonBlankTermCompleteDateIsContractInvalid() {
    assertThat(mapper.toResult("", List.of(item("DB", "DBA", true, "db02", "31/02/2020"))).status())
        .isEqualTo(UsernameRecoveryLookupResult.Status.CONTRACT_INVALID);
    assertThat(mapper.toResult("", List.of(item("DB", "DBA", true, "db02", "2020-01-01"))).status())
        .isEqualTo(UsernameRecoveryLookupResult.Status.CONTRACT_INVALID);
  }

  @Test
  void blankOrValidTermCompleteDateIsAccepted() {
    assertThat(mapper.toResult("", List.of(item("DB", "DBA", true, "db02", ""))).status())
        .isEqualTo(UsernameRecoveryLookupResult.Status.MATCHED);
    assertThat(mapper.toResult("", List.of(item("DB", "DBA", true, "db02", "15/07/1969"))).status())
        .isEqualTo(UsernameRecoveryLookupResult.Status.MATCHED);
  }

  @Test
  void nullDataItemIsContractInvalid() {
    assertThat(mapper.toResult("", Arrays.asList((TrpSearchMemberApimDataItem) null)).status())
        .isEqualTo(UsernameRecoveryLookupResult.Status.CONTRACT_INVALID);
  }

  @Test
  void envTrustCodeAndUsernameAreTrimmed() {
    var result = mapper.toResult("", List.of(item(" DB ", " DBA ", true, " db02 ")));

    assertThat(result.status()).isEqualTo(UsernameRecoveryLookupResult.Status.MATCHED);
    assertThat(result.candidates())
        .singleElement()
        .satisfies(
            candidate -> {
              assertThat(candidate.accountEnv()).isEqualTo("DB");
              assertThat(candidate.trustCode()).isEqualTo("DBA");
              assertThat(candidate.userName()).isEqualTo("db02");
            });
  }

  @Test
  void unexpectedErrorMessageThrowsDownstreamExceptionWithoutLeakingCodeInMessage() {
    var exception =
        catchThrowableOfType(
            () -> mapper.toResult("err.tpa.bad.request", List.of()),
            TrpSearchMemberApimDownstreamException.class);

    assertThat(exception.reason())
        .isEqualTo(TrpSearchMemberApimDownstreamException.Reason.UNEXPECTED_ERROR);
    assertThat(exception.downstreamErrorCode()).isEqualTo("err.tpa.bad.request");
    assertThat(exception.getMessage()).doesNotContain("err.tpa.bad.request");
  }

  @Test
  void candidateToStringNeverDisclosesUsername() {
    var result = mapper.toResult("", List.of(registeredItem("DB", "DBA", "secret.user")));

    assertThat(result.candidates().get(0).toString()).doesNotContain("secret.user");
  }

  private static TrpSearchMemberApimDataItem registeredItem(
      String env, String trustCode, String custId) {
    return item(env, trustCode, true, custId);
  }

  private static TrpSearchMemberApimDataItem unregisteredItem(String env, String trustCode) {
    return item(env, trustCode, false, null);
  }

  private static TrpSearchMemberApimDataItem item(
      String env, String trustCode, Boolean registered, String custId) {
    return item(env, trustCode, registered, custId, "");
  }

  private static TrpSearchMemberApimDataItem item(
      String env, String trustCode, Boolean registered, String custId, String termCompleteDate) {
    return new TrpSearchMemberApimDataItem(
        env, trustCode, "OE", "00000008802", "5", "", termCompleteDate, registered, custId);
  }
}
