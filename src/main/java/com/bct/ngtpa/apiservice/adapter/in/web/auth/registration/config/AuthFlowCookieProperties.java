package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config;

import java.time.Duration;

public record AuthFlowCookieProperties(String name, String path, Duration maxAge, boolean httpOnly, boolean secure, String sameSite) {
}
