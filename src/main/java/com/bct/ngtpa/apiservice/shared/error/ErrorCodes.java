package com.bct.ngtpa.apiservice.shared.error;

/**
 * Central registry of stable BFF-owned business error codes.
 *
 * <p>
 * This class is framework-free so the same contract values can be referenced
 * from application,
 * shared, and adapter layers without introducing invalid dependencies.
 */
public final class ErrorCodes {

        public static final String REQUEST_INVALID = "err.request.invalid";
        public static final String REQUEST_VALIDATION_FAILED = "err.request.validation.failed";
        public static final String REQUEST_BODY_MALFORMED = "err.request.body.malformed";

        public static final String SECURITY_ACCESS_DENIED = "err.security.access.denied";
        public static final String SECURITY_AUTHENTICATION_REQUIRED = "err.security.authentication.required";

        public static final String CACHE_UNEXPECTED = "err.cache.unexpected";

        public static final String MEMBER_CONTEXT_UNAVAILABLE = "err.member.context.unavailable";
        public static final String MEMBER_CONTEXT_INVALID = "err.member.context.invalid";

        public static final String APIM_UPSTREAM_FAILURE = "err.apim.upstream.failure";
        public static final String APIM_SERVICE_UNAVAILABLE = "err.apim.service.unavailable";
        public static final String APIM_TIMEOUT = "err.apim.timeout";
        public static final String APIM_RESPONSE_INVALID = "err.apim.response.invalid";
        public static final String APIM_UNEXPECTED = "err.apim.unexpected";

        public static final String CONFIG_ERROR_MESSAGE_MISSING = "err.config.error-message.missing";
        public static final String CONFIG_RESOLUTION_FAILED = "err.config.resolution.failed";

        public static final String CONTRIBUTION_REQUEST_INVALID = "err.contribution.request.invalid";
        public static final String NOTIFICATION_REQUEST_INVALID = "err.notification.request.invalid";

        public static final String SYSTEM_UNEXPECTED = "err.system.unexpected";
        public static final String PERSONAL_INFORMATION_UPDATE_REQUEST_INVALID = "err.personal-information.update.request.invalid";

        private ErrorCodes() {
        }
}