package com.bct.ngtpa.apiservice.shared.masking;

/**
 * Controls how many mask characters {@link MaskingService} renders between the first and last
 * revealed character of an email local-part or mobile number.
 */
public enum MaskMode {
  /** Mask-character count equals the actual number of hidden characters. */
  VARIABLE_LENGTH,
  /** Mask-character count is always the configured {@code mask-length}. */
  FIXED_LENGTH
}
