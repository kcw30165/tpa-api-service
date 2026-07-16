package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpSearchUnregMemberApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpSearchUnregMemberApimMapper;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupStatus;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Fail-first contract for the confirmed TRPSearchUnregMember behavior.
 *
 * <p>This test intentionally goes red before Patch 010 production changes. Do
 * not weaken it to preserve the old three-state lookup model.</p>
 */
class RegistrationLookupContractFailFirstTest {

    @Test
    void lookupStatusContainsOnlyTheTwoBusinessOutcomes() {
        assertThat(Arrays.stream(RegistrationLookupStatus.values())
                        .map(Enum::name)
                        .toList())
                .containsExactly("ELIGIBLE", "NOT_ELIGIBLE");
    }

    @Test
    void resultMapperRequiresOriginalRequestContactForSemanticValidation()
            throws Exception {
        Class<?> mapper = Class.forName(
                "com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper."
                        + "TrpSearchUnregMemberApimResultMapper");

        assertThat(Arrays.stream(mapper.getDeclaredMethods())
                        .filter(method -> method.getName().equals("toResult"))
                        .map(Method::getParameterTypes)
                        .map(Arrays::asList)
                        .toList())
                .anySatisfy(parameters -> assertThat(parameters)
                        .anySatisfy(type -> assertThat(type.getSimpleName())
                                .isEqualTo("RegistrationLookupCommand")));
    }

    @Test
    void unexpectedApimErrorCannotBeRepresentedAsNormalLookupStatus()
            throws Exception {
        String mapper = readMain("TrpSearchUnregMemberApimResultMapper.java");

        assertThat(mapper)
                .doesNotContain("UPSTREAM_BUSINESS_REJECTED")
                .contains("err.tpa.member.not.found")
                .contains("RegistrationLookupDownstreamException");
    }

    @Test
    void emptySuccessfulEnvelopeCannotBeTreatedAsNotEligible() throws Exception {
        String mapper = readMain("TrpSearchUnregMemberApimResultMapper.java");

        assertThat(mapper)
                .contains("RegistrationLookupDownstreamException")
                .doesNotContain("RegistrationLookupStatus.NOT_FOUND_OR_NOT_ELIGIBLE");
    }

    @Test
    void eligibleDataRequiresMatchingContactAndNonBlankAccountIdentity()
            throws Exception {
        String mapper = readMain("TrpSearchUnregMemberApimResultMapper.java");

        assertThat(mapper)
                .contains("contactValue")
                .contains("contactType")
                .contains("accountEnv")
                .contains("trustCode")
                .contains("RegistrationLookupDownstreamException");
    }

    @Test
    void feIdentityValuesMapToTheConfirmedApimValues() {
        var mapper = new TrpSearchUnregMemberApimMapper();

        assertSearchBy(mapper, RegistrationIdentityType.ID, "ID");
        assertSearchBy(mapper, RegistrationIdentityType.STAFF, "STAFF");
        assertSearchBy(mapper, RegistrationIdentityType.GPFS, "GPFS");
        assertSearchBy(mapper, RegistrationIdentityType.GPN, "GPN");
    }

    @Test
    void staffEnumKeepsTheFrontendStaffNoAlias() throws Exception {
        var field = RegistrationIdentityType.class.getField("STAFF");
        var annotation = field.getAnnotation(
                com.fasterxml.jackson.annotation.JsonProperty.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("STAFF_NO");
        assertThat(RegistrationIdentityType.STAFF.name()).isEqualTo("STAFF");
    }

    @Test
    void staleLookupStatusesAndHkidErrorAreAbsentFromProduction() throws Exception {
        String status = readMain("RegistrationLookupStatus.java");
        String pageYaml = Files.readString(Path.of(
                "src/main/resources/application-page-registration.yml"));

        assertThat(status)
                .doesNotContain("NOT_FOUND_OR_NOT_ELIGIBLE")
                .doesNotContain("UPSTREAM_BUSINESS_REJECTED");
        assertThat(pageYaml)
                .contains("endpoint: TRPSearchUnregMember")
                .doesNotContain("err.tpa.hkid.invalid");
    }

    @Test
    void errorKeyConsolidationTargetIsExplicitlyGuarded() throws Exception {
        assertThat(classExists(
                "com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey"))
                .isTrue();
        assertThat(classExists(
                "com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey"))
                .isTrue();
        assertThat(classExists(
                "com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationPublicErrorCode"))
                .isFalse();
        assertThat(classExists(
                "com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationWorkflowErrorKeys"))
                .isFalse();
    }

    private void assertSearchBy(
            TrpSearchUnregMemberApimMapper mapper,
            RegistrationIdentityType type,
            String expected) {
        TrpSearchUnregMemberApimRequest request = mapper.toApimRequest(
                new RegistrationLookupCommand(
                        type,
                        "masked-test-value",
                        "01/01/1970",
                        RegistrationContactType.EMAIL,
                        "member@example.com"));
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
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            Path path = paths
                    .filter(candidate -> candidate.getFileName().toString()
                            .equals(fileName))
                    .findFirst()
                    .orElseThrow();
            return Files.readString(path);
        }
    }
}
