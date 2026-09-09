package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpSearchMemberApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpSearchMemberApimMapper;
import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupStatus;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Fail-first contract for the confirmed TRPSearchMember behavior.
 *
 * <p>This test intentionally goes red before Patch 010 production changes. Do not weaken it to
 * preserve the old three-state lookup model.
 */
class RegistrationLookupContractFailFirstTest {

  @Test
  void lookupStatusContainsOnlyTheTwoBusinessOutcomes() {
    assertThat(Arrays.stream(RegistrationLookupStatus.values()).map(Enum::name).toList())
        .containsExactly("ELIGIBLE", "NOT_ELIGIBLE");
  }

  @Test
  void resultMapperRequiresOriginalRequestContactForSemanticValidation() throws Exception {
    Class<?> mapper =
        Class.forName(
            "com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper."
                + "TrpSearchMemberApimResultMapper");

    assertThat(
            Arrays.stream(mapper.getDeclaredMethods())
                .filter(method -> method.getName().equals("toResult"))
                .map(Method::getParameterTypes)
                .map(Arrays::asList)
                .toList())
        .anySatisfy(
            parameters ->
                assertThat(parameters)
                    .anySatisfy(
                        type ->
                            assertThat(type.getSimpleName())
                                .isEqualTo("RegistrationLookupCommand")));
  }

  @Test
  void unexpectedApimErrorCannotBeRepresentedAsNormalLookupStatus() throws Exception {
    String mapper = readMain("TrpSearchMemberApimResultMapper.java");

    assertThat(mapper)
        .doesNotContain("UPSTREAM_BUSINESS_REJECTED")
        .contains("err.tpa.member.not.found")
        .contains("RegistrationLookupDownstreamException");
  }

  @Test
  void emptySuccessfulEnvelopeCannotBeTreatedAsNotEligible() throws Exception {
    String mapper = readMain("TrpSearchMemberApimResultMapper.java");

    assertThat(mapper)
        .contains("RegistrationLookupDownstreamException.Reason.EMPTY_DATA")
        .contains("RegistrationLookupStatus.NOT_ELIGIBLE");
  }

  @Test
  void eligibleDataRequiresNonBlankAccountIdentityAndPreservesMemberFields() throws Exception {
    String mapper = readMain("TrpSearchMemberApimResultMapper.java");

    assertThat(mapper)
        .contains("item.env()")
        .contains("item.trustCode()")
        .contains("item.policyNo()")
        .contains("item.certNo()")
        .contains("item.termCompleteDate()")
        .contains("item.isRegistered()")
        .contains("item.customerId()")
        .contains("RegistrationLookupDownstreamException");
  }

  @Test
  void feIdentityValuesMapToTheConfirmedApimValues() {
    var mapper = new TrpSearchMemberApimMapper();

    assertSearchBy(mapper, MemberIdentityType.ID, "ID");
    assertSearchBy(mapper, MemberIdentityType.STAFFNO, "STAFFNO");
    assertSearchBy(mapper, MemberIdentityType.GPFS, "GPFS");
    assertSearchBy(mapper, MemberIdentityType.GPN, "GPN");
  }

  @Test
  void staleLookupStatusesAndHkidErrorAreAbsentFromProduction() throws Exception {
    String status = readMain("RegistrationLookupStatus.java");
    String pageYaml =
        Files.readString(Path.of("src/main/resources/application-page-registration.yml"));

    assertThat(status)
        .doesNotContain("NOT_FOUND_OR_NOT_ELIGIBLE")
        .doesNotContain("UPSTREAM_BUSINESS_REJECTED");
    assertThat(pageYaml).contains("endpoint: TRPSearchMember");
  }

  @Test
  void errorKeyConsolidationTargetIsExplicitlyGuarded() throws Exception {
    assertThat(
            classExists("com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey"))
        .isTrue();
    assertThat(
            classExists("com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey"))
        .isTrue();
    assertThat(
            classExists(
                "com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode"))
        .isFalse();
    assertThat(
            classExists(
                "com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationWorkflowErrorKeys"))
        .isFalse();
  }

  private void assertSearchBy(
      TrpSearchMemberApimMapper mapper, MemberIdentityType type, String expected) {
    TrpSearchMemberApimRequest request =
        mapper.toApimRequest(
            new RegistrationLookupCommand(
                type,
                "masked-test-value",
                "01/01/1970",
                RegistrationContactType.EMAIL,
                "member@example.com",
                com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR));
    assertThat(request.searchBy()).isEqualTo(expected);
  }

  private boolean classExists(String className) {
    try {
      Class.forName(className);
      return true;
    } catch (ClassNotFoundException ignored) {
      return false;
    }
  }

  private String readMain(String fileName) throws Exception {
    Path registrationMapper =
        Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/registration/mapper",
            fileName);
    if (Files.exists(registrationMapper)) {
      return Files.readString(registrationMapper);
    }
    try (var paths = Files.walk(Path.of("src/main/java"))) {
      Path path =
          paths
              .filter(candidate -> candidate.getFileName().toString().equals(fileName))
              .findFirst()
              .orElseThrow();
      return Files.readString(path);
    }
  }
}
