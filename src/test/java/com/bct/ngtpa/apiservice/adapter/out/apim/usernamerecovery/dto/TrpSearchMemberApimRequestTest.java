package com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class TrpSearchMemberApimRequestTest {

    @Test
    void retainsProvidedIdentityDobAndContactFields() {
        var request = new TrpSearchMemberApimRequest(
                "STAFFNO", "3011825", "15/07/1969", "member@example.com", "66290597");

        assertThat(request.searchBy()).isEqualTo("STAFFNO");
        assertThat(request.searchValue()).isEqualTo("3011825");
        assertThat(request.dob()).isEqualTo("15/07/1969");
        assertThat(request.email()).isEqualTo("member@example.com");
        assertThat(request.mobileNumber()).isEqualTo("66290597");
    }

    @Test
    void nullContactsAreSentAsEmptyStrings() {
        var request = new TrpSearchMemberApimRequest("ID", "A1234567", "15/07/1969", null, null);

        assertThat(request.email()).isEmpty();
        assertThat(request.mobileNumber()).isEmpty();
    }

    @Test
    void requiresIdentityValueAndDob() {
        assertThatThrownBy(() -> new TrpSearchMemberApimRequest(null, "VALUE", "15/07/1969", "", ""))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TrpSearchMemberApimRequest("ID", null, "15/07/1969", "", ""))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TrpSearchMemberApimRequest("ID", "VALUE", null, "", ""))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void jsonUsesRawApimHyphenatedFieldNames() throws Exception {
        var json = new ObjectMapper().writeValueAsString(new TrpSearchMemberApimRequest(
                "STAFFNO", "3011825", "15/07/1969", "member@example.com", "66290597"));

        assertThat(json).contains("search-by", "search-value", "dob", "email", "mobile-number");
        assertThat(json).doesNotContain("searchBy", "searchValue", "mobileNumber");
    }

    @Test
    void toStringNeverDisclosesIdentityDobOrContact() {
        var request = new TrpSearchMemberApimRequest(
                "ID", "A1234567", "15/07/1969", "member@example.com", "66290597");

        assertThat(request.toString())
                .doesNotContain("A1234567", "15/07/1969", "member@example.com", "66290597");
    }
}
