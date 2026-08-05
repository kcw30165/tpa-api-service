package com.bct.ngtpa.apiservice.application.dto.auth.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;

class RegistrationLookupModelTest {

    @Test
    void registrationLookupCommandRequiresValidatedFields() {
        assertThatThrownBy(() -> new RegistrationLookupCommand(null, "1234567890", "31/01/1980", RegistrationContactType.EMAIL, "member@example.com"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RegistrationLookupCommand(MemberIdentityType.GPFS, " ", "31/01/1980", RegistrationContactType.EMAIL, "member@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("registration lookup command fields must not be blank");
        assertThatThrownBy(() -> new RegistrationLookupCommand(MemberIdentityType.GPFS, "1234567890", " ", RegistrationContactType.EMAIL, "member@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("registration lookup command fields must not be blank");
        assertThatThrownBy(() -> new RegistrationLookupCommand(MemberIdentityType.GPFS, "1234567890", "31/01/1980", RegistrationContactType.EMAIL, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("registration lookup command fields must not be blank");

        var command = new RegistrationLookupCommand(MemberIdentityType.GPFS, "1234567890", "31/01/1980", RegistrationContactType.EMAIL, "member@example.com");
        assertThat(command.toString())
                .doesNotContain("1234567890", "member@example.com", "31/01/1980", "91234567")
                .contains("GPFS", "EMAIL");
    }

    @Test
    void lookupResultDoesNotExposeAccountsInToString() {
        var account = new RegistrationLookupAccount("member@example.com", "91234567", "OI", "JPM");
        var result = new RegistrationLookupResult(RegistrationLookupStatus.ELIGIBLE, List.of(account), null);

        assertThat(account.toString()).doesNotContain("member@example.com", "91234567",  "OI", "JPM");
        assertThat(result.toString()).doesNotContain("member@example.com", "91234567",  "OI", "JPM");
        assertThat(result.accounts()).containsExactly(account);
        assertThatThrownBy(() -> result.accounts().add(account)).isInstanceOf(UnsupportedOperationException.class);
    }
}
