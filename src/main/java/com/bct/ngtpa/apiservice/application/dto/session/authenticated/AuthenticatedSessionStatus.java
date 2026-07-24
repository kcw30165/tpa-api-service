package com.bct.ngtpa.apiservice.application.dto.session.authenticated;public enum AuthenticatedSessionStatus {ACTIVE,LOGGED_OUT,REVOKED,EXPIRED;public boolean terminal(){return this!=ACTIVE;}}
