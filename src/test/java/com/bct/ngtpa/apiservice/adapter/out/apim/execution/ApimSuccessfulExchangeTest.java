package com.bct.ngtpa.apiservice.adapter.out.apim.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialProfile;
import java.security.PublicKey;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApimSuccessfulExchangeTest {
    private final ApimCredentialProfile profile = new ApimCredentialProfile(
            "p1", "client", "secret", "api-key", "https://token", "https://apim", List.of(), "/certificate");

    @Test void associatesBodyProfileAndSuccessfulAttemptKey() {
        PublicKey key = new TestKey();
        var exchange = new ApimSuccessfulExchange("", profile, key);
        assertThat(exchange.responseBody()).isEmpty();
        assertThat(exchange.profile()).isSameAs(profile);
        assertThat(exchange.responseVerificationKey()).isSameAs(key);
    }
    @Test void permitsNullKeyOnlyAsUninterpretedNonEncryptedMetadata() {
        assertThat(new ApimSuccessfulExchange("body", profile, null).responseVerificationKey()).isNull();
    }
    @Test void rejectsNullBodyAndProfile() {
        assertThatThrownBy(() -> new ApimSuccessfulExchange(null, profile, null)).isInstanceOf(NullPointerException.class)
                .hasMessage("responseBody must not be null");
        assertThatThrownBy(() -> new ApimSuccessfulExchange("body", null, null)).isInstanceOf(NullPointerException.class)
                .hasMessage("profile must not be null");
    }
    @Test void exposesNoSecretOrBusinessFields() {
        assertThat(ApimSuccessfulExchange.class.getRecordComponents()).extracting(c -> c.getName())
                .containsExactly("responseBody", "profile", "responseVerificationKey")
                .doesNotContain("token", "apiKey", "certificate", "request", "session", "businessResult");
    }
    private static final class TestKey implements PublicKey {
        public String getAlgorithm(){ return "RSA"; } public String getFormat(){ return "X.509"; }
        public byte[] getEncoded(){ return new byte[]{1}; }
    }
}
