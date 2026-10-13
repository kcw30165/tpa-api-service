package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCryptoException;

/**
 * Shared APIM-infrastructure classification for cryptographic and response-mapping failures.
 * Response-mapping failures were reported as {@link ApimCryptoException} before {@link
 * ApimResponseMappingException} existed, so callers that turn either into an unknown outcome keep
 * the same result.
 */
public final class ApimCryptoFailureClassifier {
  private ApimCryptoFailureClassifier() {}

  public static boolean isCryptoFailure(Throwable failure) {
    return failure instanceof ApimCryptoException
        || failure instanceof ApimResponseMappingException;
  }
}
