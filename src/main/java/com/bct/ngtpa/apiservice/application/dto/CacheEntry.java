package com.bct.ngtpa.apiservice.application.dto;

/**
 * Generic cache entry returned by administrative cache scans.
 *
 * @param key fully-qualified cache key
 * @param capability cache capability segment, e.g. {@code reference-date}
 * @param qualifier capability-specific key qualifier, e.g. accountEnv for reference-date
 * @param value string cache value
 */
public record CacheEntry(
        String key,
        String capability,
        String qualifier,
        String value) {
}
