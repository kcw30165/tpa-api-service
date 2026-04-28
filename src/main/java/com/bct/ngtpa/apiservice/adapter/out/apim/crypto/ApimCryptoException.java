package com.bct.ngtpa.apiservice.adapter.out.apim.crypto;

public class ApimCryptoException extends RuntimeException {

    public ApimCryptoException(String message) {
        super(message);
    }

    public ApimCryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}