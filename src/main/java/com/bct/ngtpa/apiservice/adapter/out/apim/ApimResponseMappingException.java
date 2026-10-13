package com.bct.ngtpa.apiservice.adapter.out.apim;

/**
 * An APIM response body could not be mapped to its expected Java type, for example because it has
 * an unknown property or a value of the wrong type. Any configured field decryption has already
 * succeeded. It is kept apart from the crypto exception so that logs name the real failure. The
 * message is fixed. The Jackson cause stays attached for its field path; the outcome logger never
 * logs the cause message, because it can contain response values.
 */
public final class ApimResponseMappingException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ApimResponseMappingException(Throwable cause) {
    super("Failed to map APIM response payload.", cause);
  }
}
