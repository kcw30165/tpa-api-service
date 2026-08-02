package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCryptoException;

/** Shared APIM-infrastructure classification for cryptographic transport failures. */
public final class ApimCryptoFailureClassifier {
    private ApimCryptoFailureClassifier() {
    }

    public static boolean isCryptoFailure(Throwable failure) {
        return failure instanceof ApimCryptoException;
    }
}
