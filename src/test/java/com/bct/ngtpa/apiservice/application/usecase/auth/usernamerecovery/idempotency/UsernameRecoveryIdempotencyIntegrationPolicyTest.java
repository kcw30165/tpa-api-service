package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency;

import static org.assertj.core.api.Assertions.*;
import com.bct.ngtpa.apiservice.application.idempotency.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;

class UsernameRecoveryIdempotencyIntegrationPolicyTest {
  private final UsernameRecoveryIdempotencyIntegrationPolicy policy = new UsernameRecoveryIdempotencyIntegrationPolicy(new IdempotencyOperationIdentityFactory());

  @Test void exposesExactlyThreeSeparatedActionsAndOpaqueIdentity() {
    assertThat(UsernameRecoveryIdempotencyAction.values()).containsExactly(UsernameRecoveryIdempotencyAction.START, UsernameRecoveryIdempotencyAction.REQUEST, UsernameRecoveryIdempotencyAction.CANCEL);
    var first=policy.operationId(UsernameRecoveryIdempotencyAction.START,"scope-secret","abcdefgh");
    var other=policy.operationId(UsernameRecoveryIdempotencyAction.REQUEST,"scope-secret","abcdefgh");
    assertThat(first.capability().value()).isEqualTo("username-recovery");
    assertThat(first.scope().value()).hasSize(64).doesNotContain("scope-secret");
    assertThat(first.action()).isNotEqualTo(other.action());
    assertThat(first.toString()).doesNotContain("scope-secret","abcdefgh");
  }

  @Test void preservesEstablishedStartRequestAndCancelFingerprintBytes() throws Exception {
    assertCompatible(policy.start(List.of("STAFFNO","A123","01/01/1980","member@example.test","","captcha-1","en")), "username-recovery-start", List.of("STAFFNO","A123","01/01/1980","member@example.test","","captcha-1","en"));
    assertCompatible(policy.request(List.of("afs_123","scheme-ref")), "username-recovery-request", List.of("afs_123","scheme-ref"));
    assertCompatible(policy.cancel(List.of("afs_123")), "username-recovery-cancel", List.of("afs_123"));
  }

  @Test void exposesOnlyExplicitConstantFailureClassifiers() {
    assertThat(policy.provenPreDispatch().classify(new RuntimeException())).isEqualTo(IdempotencyFailureClassification.PROVEN_NO_SIDE_EFFECT);
    assertThat(policy.possibleSideEffect().classify(new RuntimeException())).isEqualTo(IdempotencyFailureClassification.POSSIBLE_SIDE_EFFECT);
  }

  private static void assertCompatible(IdempotencyFingerprint actual, String action, List<String> fields) throws Exception {
    MessageDigest d=MessageDigest.getInstance("SHA-256"); update(d,action); fields.forEach(v -> update(d,v));
    assertThat(actual.version()).isEqualTo(1);
    assertThat(actual.value()).isEqualTo(HexFormat.of().formatHex(d.digest()));
  }
  private static void update(MessageDigest d,String v) { byte[] b=v.getBytes(StandardCharsets.UTF_8); d.update(Integer.toString(b.length).getBytes(StandardCharsets.UTF_8)); d.update((byte)0); d.update(b); d.update((byte)0); }
}
