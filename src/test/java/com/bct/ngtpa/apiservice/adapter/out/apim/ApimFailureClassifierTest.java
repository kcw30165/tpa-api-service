package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCryptoException;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ApimFailureClassifierTest {
  @Test
  void nullAndUnrelatedFailuresFailClosedAsLocalSystem() {
    assertThat(ApimFailureClassifier.classify(null)).isEqualTo(ApimFailureCategory.LOCAL_SYSTEM);
    assertThat(ApimFailureClassifier.classify(new IllegalStateException("local")))
        .isEqualTo(ApimFailureCategory.LOCAL_SYSTEM);
  }

  @Test
  void responseInvalidApimExceptionIsContractFailure() {
    var failure = new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID, "bad");
    assertThat(ApimFailureClassifier.classify(failure))
        .isEqualTo(ApimFailureCategory.DOWNSTREAM_CONTRACT);
  }

  @Test
  void mappingAndCryptoFailuresAreInvalidResponseFailures() {
    assertThat(ApimFailureClassifier.classify(new ApimResponseMappingException(new RuntimeException("mapping"))))
        .isEqualTo(ApimFailureCategory.DOWNSTREAM_INVALID_RESPONSE);
    assertThat(ApimFailureClassifier.classify(new ApimCryptoException("crypto")))
        .isEqualTo(ApimFailureCategory.DOWNSTREAM_INVALID_RESPONSE);
  }

  @Test
  void technicalApimExceptionIsTransportFailure() {
    var failure = new ApimException(HttpStatus.SERVICE_UNAVAILABLE, "err.apim.unavailable", "down");
    assertThat(ApimFailureClassifier.classify(failure))
        .isEqualTo(ApimFailureCategory.DOWNSTREAM_TRANSPORT);
  }

  @Test
  void connectionAndTimeoutFailuresAreTransportFailures() {
    assertThat(ApimFailureClassifier.classify(new ConnectException("refused")))
        .isEqualTo(ApimFailureCategory.DOWNSTREAM_TRANSPORT);
    assertThat(ApimFailureClassifier.classify(new SocketTimeoutException("timeout")))
        .isEqualTo(ApimFailureCategory.DOWNSTREAM_TRANSPORT);
  }

  @Test
  void wrappedFailureIsClassifiedFromCauseChain() {
    var cause = new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID, "bad");
    assertThat(ApimFailureClassifier.classify(new RuntimeException("wrapper", cause)))
        .isEqualTo(ApimFailureCategory.DOWNSTREAM_CONTRACT);
  }

  @Test
  void cyclicCauseChainFailsClosedWithoutLooping() {
    var cyclic = new CyclicFailure();
    assertThat(ApimFailureClassifier.classify(cyclic))
        .isEqualTo(ApimFailureCategory.LOCAL_SYSTEM);
  }

  private static final class CyclicFailure extends RuntimeException {
    @Override
    public synchronized Throwable getCause() {
      return this;
    }
  }
}
