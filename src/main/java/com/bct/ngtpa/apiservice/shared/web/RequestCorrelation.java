package com.bct.ngtpa.apiservice.shared.web;

/**
 * Shared constants for HTTP request correlation (request-ID header propagation).
 *
 * <p>Centralises the header name and context/attribute keys so that the inbound web filter,
 * outbound APIM exchange filters, and exception handler all reference the same values without
 * importing from each other's implementation packages.
 */
public final class RequestCorrelation {

    /** Standard correlation header name echoed in every HTTP response. */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    /**
     * Exchange attribute key used to store the resolved request-ID so that the
     * exception handler can echo it in error responses.
     */
    public static final String REQUEST_ID_ATTRIBUTE_KEY =
            RequestCorrelation.class.getName() + ".requestId";

    /**
     * Reactor Context key used to propagate the request-ID through the reactive chain
     * (e.g. to outbound APIM WebClient filters and execution-logging aspects).
     */
    public static final String REQUEST_ID_CONTEXT_KEY =
            RequestCorrelation.class.getName() + ".requestId";

    private RequestCorrelation() {
    }
}
