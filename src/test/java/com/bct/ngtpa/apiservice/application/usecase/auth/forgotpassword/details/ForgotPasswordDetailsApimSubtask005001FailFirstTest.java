package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details.dto.TrpSearchRegedMemberDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details.mapper.TrpSearchRegedMemberMapper;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ForgotPasswordDetailsApimSubtask005001FailFirstTest {
    private final ForgotPasswordDetailsValidator validator = new ForgotPasswordDetailsValidator();
    private final TrpSearchRegedMemberMapper mapper = new TrpSearchRegedMemberMapper();

    @Test
    void validatesDobAndExactlyOneApprovedContact() {
        assertThat(validator.validate("31/01/1980", "Member@example.com", null).valid()).isTrue();
        assertThat(validator.validate("31/01/1980", null, "+852 9123-4567").valid()).isTrue();
        assertThat(validator.validate("", null, null).targets()).contains("dateOfBirth", "email", "mobile");
        assertThat(validator.validate("31/01/1980", "a@b.com", "91234567").targets())
                .containsExactly("email", "mobile");
        assertThat(validator.validate("31/02/1980", "bad", null).targets())
                .contains("dateOfBirth", "email");
    }

    @Test
    void mapsProtectedIdentityContextAndExactApimJson() throws Exception {
        var command = command("31/01/1980", "Member@example.com", null);
        var request = mapper.toRequest(command, "STAFF_NO", "3016365", "db05");
        String json = new ObjectMapper().writeValueAsString(request);
        assertThat(json).contains("search-by", "search-value", "dob", "email", "mobile-number", "cust-id");
        assertThat(json).doesNotContain("scheme-code", "Account-Ref", "identityType", "identityValue");
        assertThat(request.searchBy()).isEqualTo("STAFFNO");
        assertThat(request.mobileNumber()).isEmpty();
        assertThat(request.toString()).doesNotContain("3016365", "db05", "Member@example.com", "31/01/1980");
    }

    @Test
    void classifiesExactTasOutcomesAndCardinality() {
        var command = command("31/01/1980", "Member@example.com", null);
        assertThat(mapper.toResult("err.tpa.member.not.found", List.of(), command).status())
                .isEqualTo(ForgotPasswordDetailsLookupStatus.NOT_FOUND);
        assertThatThrownBy(() -> mapper.toResult("err.tpa.unexpected", List.of(), command))
                .isInstanceOf(ForgotPasswordDetailsDownstreamException.class);
        assertThatThrownBy(() -> mapper.toResult("", List.of(), command))
                .isInstanceOf(ForgotPasswordDetailsDownstreamException.class);
        assertThatThrownBy(() -> mapper.toResult("", List.of(
                new TrpSearchRegedMemberDataItem("member@example.com", "", "DB", "DBA"),
                new TrpSearchRegedMemberDataItem("member@example.com", "", "DB", "DBA")), command))
                .isInstanceOf(ForgotPasswordDetailsDownstreamException.class);
        assertThat(mapper.toResult("", List.of(
                new TrpSearchRegedMemberDataItem("member@example.com", "", null, null)), command).status())
                .isEqualTo(ForgotPasswordDetailsLookupStatus.MATCHED);
    }

    private ForgotPasswordDetailsCommand command(String dob, String email, String mobile) {
        return new ForgotPasswordDetailsCommand(new AuthFlowSessionId("afs_opaque"), "csrf_opaque",
                "https://portal.example", null, "req-001", dob, email, mobile, Duration.ofSeconds(10));
    }
}
