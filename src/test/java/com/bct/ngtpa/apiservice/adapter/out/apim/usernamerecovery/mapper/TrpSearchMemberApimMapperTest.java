package com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryLookupCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class TrpSearchMemberApimMapperTest {
    private final TrpSearchMemberApimMapper mapper = new TrpSearchMemberApimMapper();

    @Test
    void mapsIdentityDobEmailAndEmptyUnusedMobile() {
        var request = mapper.toApimRequest(new UsernameRecoveryLookupCommand(
                MemberIdentityType.STAFFNO, "3011825", "15/07/1969", "member@example.com", null));

        assertThat(request.searchBy()).isEqualTo("STAFFNO");
        assertThat(request.searchValue()).isEqualTo("3011825");
        assertThat(request.dob()).isEqualTo("15/07/1969");
        assertThat(request.email()).isEqualTo("member@example.com");
        assertThat(request.mobileNumber()).isEmpty();
    }

    @Test
    void mapsMobileAndEmptyUnusedEmail() {
        var request = mapper.toApimRequest(new UsernameRecoveryLookupCommand(
                MemberIdentityType.ID, "A1234567", "15/07/1969", null, "66290597"));

        assertThat(request.searchBy()).isEqualTo("ID");
        assertThat(request.email()).isEmpty();
        assertThat(request.mobileNumber()).isEqualTo("66290597");
    }

    @Test
    void mapsEveryIdentityTypeDirectlyToSearchBy() {
        for (MemberIdentityType type : MemberIdentityType.values()) {
            var request = mapper.toApimRequest(new UsernameRecoveryLookupCommand(
                    type, "VALUE", "15/07/1969", "member@example.com", null));

            assertThat(request.searchBy()).isEqualTo(type.name());
        }
    }

    @Test
    void rejectsNullCommand() {
        assertThatThrownBy(() -> mapper.toApimRequest(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void mappedRequestUsesRawHyphenatedJsonFieldNamesAndNoApplicationNames() throws Exception {
        var request = mapper.toApimRequest(new UsernameRecoveryLookupCommand(
                MemberIdentityType.STAFFNO, "3011825", "15/07/1969", "member@example.com", "66290597"));

        var json = new ObjectMapper().writeValueAsString(request);

        assertThat(json).contains("search-by", "search-value", "dob", "email", "mobile-number");
        assertThat(json).doesNotContain(
                "identityType", "identityValue", "dateOfBirth", "accountRef", "requestId");
    }
}
