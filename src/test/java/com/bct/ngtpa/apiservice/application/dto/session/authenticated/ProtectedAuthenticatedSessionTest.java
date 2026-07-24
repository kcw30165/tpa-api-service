package com.bct.ngtpa.apiservice.application.dto.session.authenticated;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionProtectorPort;
import static org.assertj.core.api.Assertions.assertThat;import static org.assertj.core.api.Assertions.assertThatThrownBy;import org.junit.jupiter.api.Test;
class ProtectedAuthenticatedSessionTest {
 @Test void protectedWireValueContainsVersionAndSeparatedAeadParts(){var p=new ProtectedAuthenticatedSession("v1","cipher-secret-value","nonce-secret-value","tag-secret-value");assertThat(p.toString()).contains("ciphertext=<redacted>","nonce=<redacted>","authenticationTag=<redacted>").doesNotContain("cipher-secret-value","nonce-secret-value","tag-secret-value");assertThat(p.version()).isEqualTo("v1");}
 @Test void blankWirePartsFailClosed(){assertThatThrownBy(()->new ProtectedAuthenticatedSession("v1"," ","nonce","tag")).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->new ProtectedAuthenticatedSession("v1","cipher","nonce"," ")).isInstanceOf(IllegalArgumentException.class);}
 @Test void protectionPortIsInfrastructureNeutral(){assertThat(AuthenticatedSessionProtectorPort.class.getDeclaredMethods()).extracting(java.lang.reflect.Method::getName).containsExactlyInAnyOrder("protect","unprotect");}
}
