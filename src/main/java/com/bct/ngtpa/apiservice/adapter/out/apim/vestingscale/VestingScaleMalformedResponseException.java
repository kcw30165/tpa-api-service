package com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale;

final class VestingScaleMalformedResponseException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  VestingScaleMalformedResponseException() {
    super("Malformed vesting-scale APIM response");
  }
}
