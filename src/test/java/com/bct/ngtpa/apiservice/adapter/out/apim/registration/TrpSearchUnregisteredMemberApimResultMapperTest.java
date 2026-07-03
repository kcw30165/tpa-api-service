package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpSearchUnregisteredMemberApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpSearchUnregisteredMemberApimResultMapper;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrpSearchUnregisteredMemberApimResultMapperTest {
    private final TrpSearchUnregisteredMemberApimResultMapper mapper = new TrpSearchUnregisteredMemberApimResultMapper();

    @Test
    void apimDataItemJsonMapsHyphenatedFields() throws Exception {
        var json = """
                {
                  "email": "member@example.com",
                  "mobile-number": "",
                  "policy-no": "00000008802",
                  "cert-no": 2,
                  "env": "OI",
                  "trust-code": "JPM"
                }
                """;

        var item = new ObjectMapper().readValue(json, TrpSearchUnregisteredMemberApimDataItem.class);

        assertThat(item.email()).isEqualTo("member@example.com");
        assertThat(item.mobileNumber()).isEmpty();
        assertThat(item.policyNo()).isEqualTo("00000008802");
        assertThat(item.certNo()).isEqualTo("2");
        assertThat(item.env()).isEqualTo("OI");
        assertThat(item.trustCode()).isEqualTo("JPM");
    }

    @Test
    void resultMapperReturnsEligibleForEmptyErrMessageAndNonEmptyData() {
        var result = mapper.toResult("", List.of(item()));

        assertThat(result.status()).isEqualTo(RegistrationLookupStatus.ELIGIBLE);
        assertThat(result.accounts()).hasSize(1);
        assertThat(result.upstreamErrorCode()).isNull();
    }

    @Test
    void resultMapperReturnsNotFoundForMemberNotFound() {
        var result = mapper.toResult("err.tpa.member.not.found", List.of());

        assertThat(result.status()).isEqualTo(RegistrationLookupStatus.NOT_FOUND_OR_NOT_ELIGIBLE);
        assertThat(result.upstreamErrorCode()).isEqualTo("err.tpa.member.not.found");
        assertThat(result.accounts()).isEmpty();
    }

    @Test
    void resultMapperReturnsNotFoundForEmptySuccessfulData() {
        var result = mapper.toResult("", List.of());

        assertThat(result.status()).isEqualTo(RegistrationLookupStatus.NOT_FOUND_OR_NOT_ELIGIBLE);
        assertThat(result.accounts()).isEmpty();
        assertThat(result.upstreamErrorCode()).isNull();
    }

    @Test
    void resultMapperReturnsBusinessRejectedForOtherErrMessage() {
        var result = mapper.toResult("err.tpa.some.other.business.error", List.of());

        assertThat(result.status()).isEqualTo(RegistrationLookupStatus.UPSTREAM_BUSINESS_REJECTED);
        assertThat(result.upstreamErrorCode()).isEqualTo("err.tpa.some.other.business.error");
        assertThat(result.accounts()).isEmpty();
    }

    @Test
    void apimDtosToStringDoNotExposePii() {
        assertThat(item().toString()).doesNotContain("member@example.com", "91234567", "00000008802", "2", "31/01/1980");
    }

    private static TrpSearchUnregisteredMemberApimDataItem item() {
        return new TrpSearchUnregisteredMemberApimDataItem(
                "member@example.com",
                "91234567",
                "00000008802",
                "2",
                "OI",
                "JPM");
    }
}
