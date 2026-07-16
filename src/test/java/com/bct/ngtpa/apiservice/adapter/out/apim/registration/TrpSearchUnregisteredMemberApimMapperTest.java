package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpSearchUnregMemberApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpSearchUnregMemberApimMapper;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class TrpSearchUnregMemberApimMapperTest {
    private final TrpSearchUnregMemberApimMapper mapper = new TrpSearchUnregMemberApimMapper();

    @Test
    void apimRequestMapsEmailContact() {
        var request = mapper.toApimRequest(new RegistrationLookupCommand(
                RegistrationIdentityType.GPFS,
                "1234567890",
                "31/01/1980",
                RegistrationContactType.EMAIL,
                "member@example.com"));

        assertThat(request.searchBy()).isEqualTo("GPFS");
        assertThat(request.searchValue()).isEqualTo("1234567890");
        assertThat(request.dob()).isEqualTo("31/01/1980");
        assertThat(request.email()).isEqualTo("member@example.com");
        assertThat(request.mobileNumber()).isEmpty();
    }

    @Test
    void apimRequestMapsMobileContact() {
        var request = mapper.toApimRequest(new RegistrationLookupCommand(
                RegistrationIdentityType.ID,
                "A1234567",
                "15/01/1980",
                RegistrationContactType.MOBILE,
                "91234567"));

        assertThat(request.searchBy()).isEqualTo("ID");
        assertThat(request.searchValue()).isEqualTo("A1234567");
        assertThat(request.dob()).isEqualTo("15/01/1980");
        assertThat(request.email()).isEmpty();
        assertThat(request.mobileNumber()).isEqualTo("91234567");
    }

    @Test
    void apimRequestUsesIdentityTypeDirectly() {
        for (RegistrationIdentityType type : RegistrationIdentityType.values()) {
            var request = mapper.toApimRequest(new RegistrationLookupCommand(
                    type,
                    "SAFEVALUE",
                    "31/01/1980",
                    RegistrationContactType.EMAIL,
                    "member@example.com"));
            assertThat(request.searchBy()).isEqualTo(type.name());
        }
    }

    @Test
    void apimRequestJsonUsesExpectedHyphenatedFieldNames() throws Exception {
        var json = new ObjectMapper().writeValueAsString(new TrpSearchUnregMemberApimRequest(
                "GPFS",
                "1234567890",
                "31/01/1980",
                "member@example.com",
                ""));

        assertThat(json).contains("search-by", "search-value", "dob", "email", "mobile-number");
        assertThat(json).doesNotContain("identityType", "identityTypeSearchScope", "dataDomain", "accountRef", "lang", "requestId");
    }

    @Test
    void apimDtosToStringDoNotExposePii() {
        var request = new TrpSearchUnregMemberApimRequest(
                "GPFS",
                "1234567890",
                "31/01/1980",
                "member@example.com",
                "91234567");

        assertThat(request.toString()).doesNotContain("1234567890", "member@example.com", "91234567", "31/01/1980");
    }
}
