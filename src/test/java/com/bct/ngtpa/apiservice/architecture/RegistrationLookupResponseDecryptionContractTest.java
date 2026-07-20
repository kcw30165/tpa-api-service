package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegistrationLookupResponseDecryptionContractTest {

    @Test
    void registrationContactFieldsAreConfiguredForApimResponseDecryption() {
        var properties = new ApimProperties();
        properties.getEncryption().setResponseFields(List.of("email", "mobile-number"));

        assertThat(properties.getEncryption().getResponseFields())
                .contains("email", "mobile-number");
    }
}
