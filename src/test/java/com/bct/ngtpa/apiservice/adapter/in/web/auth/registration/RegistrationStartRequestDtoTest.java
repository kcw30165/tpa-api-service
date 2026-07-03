package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationStartRequest;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class RegistrationStartRequestDtoTest {

    @Test
    void registrationStartRequestDoesNotExposeRemovedPayloadFields() {
        assertThat(Arrays.stream(RegistrationStartRequest.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("identityType", "identityValue", "dateOfBirth", "email", "mobile", "captchaChallengeId")
                .doesNotContain("registrationSessionId", "authFlowSessionId", "otpChallengeId", "contactType", "contactValue", "accountRef", "lang", "requestId");
    }
}
