package com.bct.ngtpa.apiservice.exception;

/**
 * Runtime exception for Redis cache outbound adapter failures.
 *
 * <p>The message is always a generic, safe description of the operation that failed
 * (e.g. {@code "Cache get operation failed"}). Raw Redis error messages, connection
 * details, passwords, certificate paths, and Sentinel addresses are never included
 * in the message — they are only present on the wrapped {@link #getCause()}.
 *
 * <p>The web exception handler should map this to an appropriate HTTP status code
 * (typically 503 Service Unavailable) when it propagates to the web layer.
 */
public class CacheException extends RuntimeException {

    public CacheException(String message) {
        super(message);
    }

    public CacheException(String message, Throwable cause) {
        super(message, cause);
    }
}
