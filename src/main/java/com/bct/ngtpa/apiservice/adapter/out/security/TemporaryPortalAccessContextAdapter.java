package com.bct.ngtpa.apiservice.adapter.out.security;

import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.MemberOwnerContext;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
import com.bct.ngtpa.apiservice.application.port.out.PortalAccessContextPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
@Slf4j
public class TemporaryPortalAccessContextAdapter implements PortalAccessContextPort {

        private static final String SOURCE_NAME = "temporary-portal-access-context";
        private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final TemporaryPortalAccessContextProperties properties;

    @Override
    public Mono<PortalAccessContext> resolvePortalAccessContext(String accountRef) {
        var profile = properties.getProfiles().get(accountRef);
        if (profile == null) {
            return Mono.error(new PortalAccessContextResolutionException(
                    "No temporary portal access context profile configured for accountRef: " + accountRef));
        }
        TermStatus termStatus = mapTermStatus(profile.getTermStatus(), accountRef, profile.getAccountEnv());
        return Mono.just(new PortalAccessContext(
                new ActorContext(
                        profile.getActorUserId(),
                        profile.getActorUserType(),
                        profile.getActorUserRole()),
                new MemberOwnerContext(
                        profile.getMemberUserId(),
                        profile.getMemberType()),
                new AccountContext(
                        accountRef,
                        profile.getAccountEnv(),
                        profile.getPolicyNo(),
                        profile.getCertNo(),
                        profile.getTrustCode(),
                        profile.getSchemeType(),
                                                termStatus,
                                                parseTermCompletionDate(profile.getTermCompletionDate()))));
    }

        private TermStatus mapTermStatus(String rawTermStatus, String accountRef, String accountEnv) {
                TermStatus termStatus = TermStatus.fromCode(rawTermStatus);
                if (termStatus == TermStatus.UNKNOWN && rawTermStatus != null && !rawTermStatus.isBlank()) {
                        log.warn(
                                        "event=portal_access_context_term_status_unknown source={} accountRef={} accountEnv={} rawTermStatus={}",
                                        SOURCE_NAME,
                                        sanitizeForLog(accountRef),
                                        sanitizeForLog(accountEnv),
                                        sanitizeForLog(rawTermStatus));
                }
                return termStatus;
        }

        private LocalDate parseTermCompletionDate(String rawTermCompletionDate) {
                if (rawTermCompletionDate == null || rawTermCompletionDate.isBlank()) {
                        return null;
                }
                return LocalDate.parse(rawTermCompletionDate.trim(), DATE_FORMATTER);
        }

        private String sanitizeForLog(String value) {
                if (value == null) {
                        return "";
                }
                return value
                                .replace('\r', ' ')
                                .replace('\n', ' ')
                                .replace('\t', ' ')
                                .trim();
        }
}
