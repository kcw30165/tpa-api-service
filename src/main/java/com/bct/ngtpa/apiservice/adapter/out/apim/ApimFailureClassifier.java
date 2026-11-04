package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/** Pure, framework-free technical classifier. Feature-owned business rejections are out of scope. */
public final class ApimFailureClassifier {
  private ApimFailureClassifier() {}

  public static ApimFailureCategory classify(Throwable failure) {
    Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    Throwable current = failure;
    while (current != null && visited.add(current)) {
      if (ApimCryptoFailureClassifier.isCryptoFailure(current) || responseInvalid(current)) {
        return ApimFailureCategory.DOWNSTREAM_CONTRACT;
      }
      if (current instanceof ApimException || transport(current)) {
        return ApimFailureCategory.DOWNSTREAM_TRANSPORT;
      }
      current = current.getCause();
    }
    return ApimFailureCategory.LOCAL_SYSTEM;
  }

  private static boolean responseInvalid(Throwable failure) {
    return failure instanceof ApimException apim
        && ErrorCodes.APIM_RESPONSE_INVALID.equals(apim.getErrorCode());
  }

  private static boolean transport(Throwable failure) {
    return failure instanceof ConnectException
        || failure instanceof InterruptedIOException
        || failure instanceof SocketException
        || failure instanceof UnknownHostException
        || failure instanceof TimeoutException;
  }
}
