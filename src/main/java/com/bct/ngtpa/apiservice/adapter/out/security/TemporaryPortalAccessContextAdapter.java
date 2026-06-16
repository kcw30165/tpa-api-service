package com.bct.ngtpa.apiservice.adapter.out.security;

import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.MemberOwnerContext;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
import com.bct.ngtpa.apiservice.application.port.out.PortalAccessContextPort;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class TemporaryPortalAccessContextAdapter implements PortalAccessContextPort {

    private static final String SOURCE_NAME = "temporary-portal-access-context";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final TemporaryPortalAccessContextProperties properties;

    @Override
    public Mono<PortalAccessContext> resolvePortalAccessContext(String accountRef) {
        if (!StringUtils.hasText(accountRef)) {
            return Mono.error(new PortalAccessContextResolutionException(
                    ErrorCodes.MEMBER_CONTEXT_INVALID,
                    "No temporary portal access context profile configured for accountRef: "
                            + (accountRef == null ? "null" : accountRef)));
        }

        if (properties.getSessions() != null && !properties.getSessions().isEmpty()) {
            return resolveFromSessionShape(accountRef.trim());
        }
        return resolveFromLegacyProfileShape(accountRef.trim());
    }

    private Mono<PortalAccessContext> resolveFromSessionShape(String accountRef) {
        String sessionId = resolveDefaultSessionId(properties.getSessions());
        TemporaryPortalAccessContextProperties.SessionProfile session = properties.getSessions().get(sessionId);
        if (session == null) {
            return Mono.error(new PortalAccessContextResolutionException(
                    ErrorCodes.MEMBER_CONTEXT_INVALID,
                    "No temporary portal access context session configured for sessionId: " + sessionId));
        }
        TemporaryPortalAccessContextProperties.AccountProfile account = session.getAccounts().get(accountRef);
        if (account == null) {
            return Mono.error(new PortalAccessContextResolutionException(
                    ErrorCodes.MEMBER_CONTEXT_INVALID,
                    "No temporary portal access context account configured for accountRef: "
                            + accountRef + " in session: " + sessionId));
        }
        var actor = session.getActor() == null
                ? new TemporaryPortalAccessContextProperties.SessionActor()
                : session.getActor();
        TermStatus termStatus = mapTermStatus(account.getTermStatus(), accountRef, account.getAccountEnv());
        return Mono.just(new PortalAccessContext(
                new ActorContext(actor.getActorUserId(), actor.getActorUserRole()),
                new MemberOwnerContext("", ""),
                new AccountContext(
                        accountRef,
                        account.getAccountEnv(),
                        account.getPolicyNo(),
                        account.getCertNo(),
                        account.getTrustCode(),
                        account.getSchemeType(),
                        termStatus,
                        parseTermCompletionDate(account.getTermCompletionDate(), accountRef))));
    }

    private String resolveDefaultSessionId(Map<String, TemporaryPortalAccessContextProperties.SessionProfile> sessions) {
        if (StringUtils.hasText(properties.getDefaultSessionId())) {
            return properties.getDefaultSessionId().trim();
        }
        return sessions.keySet().stream().findFirst().orElse("");
    }

    private Mono<PortalAccessContext> resolveFromLegacyProfileShape(String accountRef) {
        var profile = properties.getProfiles().get(accountRef);
        if (profile == null) {
            return Mono.error(new PortalAccessContextResolutionException(
                    ErrorCodes.MEMBER_CONTEXT_INVALID,
                    "No temporary portal access context profile configured for accountRef: " + accountRef));
        }
        TermStatus termStatus = mapTermStatus(profile.getTermStatus(), accountRef, profile.getAccountEnv());
        return Mono.just(new PortalAccessContext(
                new ActorContext(profile.getActorUserId(), profile.getActorUserRole()),
                new MemberOwnerContext(profile.getMemberUserId(), profile.getMemberType()),
                new AccountContext(
                        accountRef,
                        profile.getAccountEnv(),
                        profile.getPolicyNo(),
                        profile.getCertNo(),
                        profile.getTrustCode(),
                        profile.getSchemeType(),
                        termStatus,
                        parseTermCompletionDate(profile.getTermCompletionDate(), accountRef))));
    }

    private TermStatus mapTermStatus(String rawCode, String accountRef, String accountEnv) {
        TermStatus status = TermStatus.fromCode(rawCode);
        if (status == TermStatus.UNKNOWN) {
            log.warn("Unknown temporary portal access context termStatus. source={}, accountRef={}, accountEnv={}, rawTermStatus={}",
                    SOURCE_NAME, accountRef, accountEnv, rawCode);
        }
        return status;
    }

    private LocalDate parseTermCompletionDate(String rawDate, String accountRef) {
        if (!StringUtils.hasText(rawDate)) {
            return null;
        }
        try {
            return LocalDate.parse(rawDate.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException exception) {
            log.warn("Invalid temporary portal access context termCompletionDate. source={}, accountRef={}, rawTermCompletionDate={}",
                    SOURCE_NAME, accountRef, rawDate);
            return null;
        }
    }
}
