package com.bct.ngtpa.apiservice.architecture;
import static org.assertj.core.api.Assertions.assertThat;import java.nio.file.*;import org.junit.jupiter.api.Test;
class Task0043LoginStartOrchestrationArchitectureTest {
 @Test void orchestrationUsesApprovedFoundationsAndStoresOnlyEncryptedContext() throws Exception {String s=Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/start/StartLoginService.java"));assertThat(s).contains("LoginCredentialPolicyService","LoginCredentialIdempotencyCoordinator","CredentialAuthenticationPort","LoginPreAuthContextProtectorPort","AuthFlowSessionLifecycleService","AuthFlowSessionCsrfService","AuthFlowScenario.LOGIN");assertThat(s).doesNotContain("WebClient","Redis","ServerHttpResponse","@PostMapping");}
 @Test void publicResultDoesNotExposeRawDestinationOrProtectedCiphertext() throws Exception {String s=Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/start/StartLoginResult.java"));assertThat(s).contains("destinations","csrfToken","authFlowSessionId");assertThat(s).doesNotContain("rawDestination","ciphertext","normalizedUsername","password");}
}
