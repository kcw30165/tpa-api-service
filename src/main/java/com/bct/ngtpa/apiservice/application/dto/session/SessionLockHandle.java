package com.bct.ngtpa.apiservice.application.dto.session;
public record SessionLockHandle(boolean acquired, String lockKey, String ownershipToken) {
    public SessionLockHandle {
        if (acquired) {
            if (lockKey == null || lockKey.isBlank()) throw new IllegalArgumentException("lockKey must not be blank when acquired");
            if (ownershipToken == null || ownershipToken.isBlank()) throw new IllegalArgumentException("ownershipToken must not be blank when acquired");
        }
    }
    public static SessionLockHandle notAcquired() { return new SessionLockHandle(false, null, null); }
    @Override public String toString() { return "SessionLockHandle[acquired=" + acquired + ", lockKey=<redacted>, ownershipToken=<redacted>]"; }
}
