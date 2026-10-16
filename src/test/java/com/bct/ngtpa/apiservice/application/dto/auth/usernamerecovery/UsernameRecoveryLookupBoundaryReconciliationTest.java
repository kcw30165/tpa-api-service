package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Task 018 reconciliation of the Username Recovery lookup application boundary.
 *
 * <p>Confirms that the committed lookup command/result/candidate contracts represent exactly the
 * final-design outcome set and minimal projection: no public {@code ApiStatus} vocabulary such as
 * {@code DOWNSTREAM_REJECTED}, no raw APIM field names in string output, no transport-only fields,
 * and framework-free immutable records. These tests confirm the committed Task 007/009 contract
 * ({@code EXPECTED_EXISTING_CONTRACT_CONFIRMED}); they are not fail-first defect tests.
 */
class UsernameRecoveryLookupBoundaryReconciliationTest {

  @Test
  void lookupResultStatusIsExactlyTheCommittedOutcomeSet() {
    assertThat(UsernameRecoveryLookupResult.Status.values())
        .containsExactly(
            UsernameRecoveryLookupResult.Status.MATCHED,
            UsernameRecoveryLookupResult.Status.NOT_FOUND,
            UsernameRecoveryLookupResult.Status.CONTRACT_INVALID,
            UsernameRecoveryLookupResult.Status.UNAVAILABLE,
            UsernameRecoveryLookupResult.Status.UNKNOWN_OUTCOME);
  }

  @Test
  void lookupOutcomeSetIsDisjointFromPublicApiStatusVocabulary() {
    List<String> names =
        Arrays.stream(UsernameRecoveryLookupResult.Status.values()).map(Enum::name).toList();

    assertThat(names)
        .isNotEmpty()
        .doesNotContain(
            "DOWNSTREAM_REJECTED",
            "DOWNSTREAM_ERROR",
            "BUSINESS_REJECTED",
            "SYSTEM_ERROR",
            "VALIDATION_FAILED",
            "SUCCESS");
  }

  @Test
  void lookupCandidateExposesOnlyTheMinimalProjection() {
    // Amended for docs/design/10-template-service/template-service-solution-design.md
    // section 8.2: schemeName/schemeNameChi are legitimate application-level content
    // fields (already normalized to camelCase, sourced from the APIM sch-name/
    // chin-sch-name response fields) required by the centralized Template Service to
    // render {schemeName}/{schemeNameChi} placeholders. This does not violate this
    // test's original intent (no raw APIM field names, no transport-only cruft, no
    // framework dependency, framework-free immutable record) - it amends the "minimal
    // projection" contract to include two new, intentionally-added content fields.
    assertThat(UsernameRecoveryLookupCandidate.class.isRecord()).isTrue();
    assertThat(recordComponents(UsernameRecoveryLookupCandidate.class))
        .containsExactly(
            "accountEnv", "trustCode", "registered", "userName", "schemeName", "schemeNameChi");
  }

  @Test
  void lookupCommandExposesOnlyTheCommittedInputFields() {
    assertThat(UsernameRecoveryLookupCommand.class.isRecord()).isTrue();
    assertThat(recordComponents(UsernameRecoveryLookupCommand.class))
        .containsExactly(
            "identityType", "identityValue", "dateOfBirth", "email", "mobile", "actorUserRole");
  }

  @Test
  void lookupResultExposesOnlyStatusAndCandidates() {
    assertThat(UsernameRecoveryLookupResult.class.isRecord()).isTrue();
    assertThat(recordComponents(UsernameRecoveryLookupResult.class))
        .containsExactly("status", "candidates");
  }

  @Test
  void lookupStringOutputsNeverExposeApimRawNamesOrSensitiveValues() {
    var candidate = new UsernameRecoveryLookupCandidate("DB", "DBA", true, "john.doe", null, null);
    var matched =
        new UsernameRecoveryLookupResult(
            UsernameRecoveryLookupResult.Status.MATCHED, List.of(candidate));
    var command =
        new UsernameRecoveryLookupCommand(
            MemberIdentityType.STAFFNO,
            "3011825",
            "15/07/1969",
            "member@example.com",
            "66290597",
            com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR);

    assertThat(candidate.toString())
        .doesNotContain("john.doe")
        .doesNotContain("trust-code", "policy-no", "cert-no", "cust-id", "err-message");
    assertThat(matched.toString())
        .doesNotContain("john.doe")
        .doesNotContain("trust-code", "policy-no", "cert-no", "cust-id", "err-message");
    assertThat(command.toString())
        .doesNotContain("3011825", "15/07/1969", "member@example.com", "66290597")
        .doesNotContain("search-by", "search-value", "mobile-number", "err-message");
  }

  private static List<String> recordComponents(Class<?> type) {
    return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
  }
}
