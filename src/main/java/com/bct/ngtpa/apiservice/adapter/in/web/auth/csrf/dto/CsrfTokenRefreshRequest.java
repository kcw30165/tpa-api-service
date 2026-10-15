package com.bct.ngtpa.apiservice.adapter.in.web.auth.csrf.dto;

import com.bct.ngtpa.apiservice.application.dto.auth.csrf.CsrfRefreshSessionType;

/**
 * Optional request body for {@code POST /api/v1/auth/csrf-token/refresh}.
 *
 * <p>Disambiguates which session to refresh when both the authenticated ({@code portal_sid}) and
 * pre-auth ({@code auth_flow_sid}) cookies are present and valid at once - for example, a user
 * logged in while concurrently running a separate pre-auth flow such as Forgot Username in another
 * tab. When {@code sessionType} is absent (or the body itself is absent), behavior is unchanged:
 * the authenticated session wins whenever its cookie resolves.
 */
public record CsrfTokenRefreshRequest(CsrfRefreshSessionType sessionType) {}
