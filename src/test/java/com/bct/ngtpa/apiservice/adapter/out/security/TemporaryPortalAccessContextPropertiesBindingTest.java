package com.bct.ngtpa.apiservice.adapter.out.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TemporaryPortalAccessContextPropertiesBindingTest {

    @Test
    void bindsAllFieldsForNotificationsProfile() {
        var props = Map.ofEntries(
            Map.entry("temporary-portal-access-context.profiles.notifications.actor-user-id", "actor_user_notifications"),
            Map.entry("temporary-portal-access-context.profiles.notifications.actor-user-type", "MEMBER"),
            Map.entry("temporary-portal-access-context.profiles.notifications.actor-user-role", "SELF"),
            Map.entry("temporary-portal-access-context.profiles.notifications.member-user-id", "member_user_notifications"),
            Map.entry("temporary-portal-access-context.profiles.notifications.member-type", "MBR"),
            Map.entry("temporary-portal-access-context.profiles.notifications.account-env", "JP"),
            Map.entry("temporary-portal-access-context.profiles.notifications.policy-no", "policyNo_for_notifications"),
            Map.entry("temporary-portal-access-context.profiles.notifications.cert-no", "certNo_for_notifications"),
            Map.entry("temporary-portal-access-context.profiles.notifications.trust-code", "trustCode_for_notifications"),
            Map.entry("temporary-portal-access-context.profiles.notifications.scheme-type", "schemeType_for_notifications"),
            Map.entry("temporary-portal-access-context.profiles.notifications.term-status", "O"),
            Map.entry("temporary-portal-access-context.profiles.notifications.term-completion-date", "31/03/2026")
        );

        var bound = bind(props);
        var profile = bound.getProfiles().get("notifications");

        assertNotNull(profile, "notifications profile must be present");
        assertEquals("actor_user_notifications", profile.getActorUserId());
        assertEquals("MEMBER", profile.getActorUserType());
        assertEquals("SELF", profile.getActorUserRole());
        assertEquals("member_user_notifications", profile.getMemberUserId());
        assertEquals("MBR", profile.getMemberType());
        assertEquals("JP", profile.getAccountEnv());
        assertEquals("policyNo_for_notifications", profile.getPolicyNo());
        assertEquals("certNo_for_notifications", profile.getCertNo());
        assertEquals("trustCode_for_notifications", profile.getTrustCode());
        assertEquals("schemeType_for_notifications", profile.getSchemeType());
        assertEquals("O", profile.getTermStatus());
        assertEquals("31/03/2026", profile.getTermCompletionDate());
    }

    @Test
    void bindsAllFieldsForContributionsProfile() {
        var props = Map.ofEntries(
            Map.entry("temporary-portal-access-context.profiles.contributions.actor-user-id", "actor_user_contributions"),
            Map.entry("temporary-portal-access-context.profiles.contributions.actor-user-type", "MEMBER"),
            Map.entry("temporary-portal-access-context.profiles.contributions.actor-user-role", "SELF"),
            Map.entry("temporary-portal-access-context.profiles.contributions.member-user-id", "member_user_contributions"),
            Map.entry("temporary-portal-access-context.profiles.contributions.member-type", "MBR"),
            Map.entry("temporary-portal-access-context.profiles.contributions.account-env", "JP"),
            Map.entry("temporary-portal-access-context.profiles.contributions.policy-no", "policyNo_for_contributions"),
            Map.entry("temporary-portal-access-context.profiles.contributions.cert-no", "certNo_for_contributions"),
            Map.entry("temporary-portal-access-context.profiles.contributions.trust-code", "trustCode_for_contributions"),
            Map.entry("temporary-portal-access-context.profiles.contributions.scheme-type", "schemeType_for_contributions"),
            Map.entry("temporary-portal-access-context.profiles.contributions.term-status", "P"),
            Map.entry("temporary-portal-access-context.profiles.contributions.term-completion-date", "30/04/2026")
        );

        var bound = bind(props);
        var profile = bound.getProfiles().get("contributions");

        assertNotNull(profile, "contributions profile must be present");
        assertEquals("actor_user_contributions", profile.getActorUserId());
        assertEquals("MEMBER", profile.getActorUserType());
        assertEquals("SELF", profile.getActorUserRole());
        assertEquals("member_user_contributions", profile.getMemberUserId());
        assertEquals("MBR", profile.getMemberType());
        assertEquals("JP", profile.getAccountEnv());
        assertEquals("policyNo_for_contributions", profile.getPolicyNo());
        assertEquals("certNo_for_contributions", profile.getCertNo());
        assertEquals("trustCode_for_contributions", profile.getTrustCode());
        assertEquals("schemeType_for_contributions", profile.getSchemeType());
        assertEquals("P", profile.getTermStatus());
        assertEquals("30/04/2026", profile.getTermCompletionDate());
    }

    @Test
    void bindsBothProfilesTogether() {
        var props = Map.of(
                "temporary-portal-access-context.profiles.notifications.actor-user-id", "notif-actor",
                "temporary-portal-access-context.profiles.notifications.policy-no", "notif-policyNo",
                "temporary-portal-access-context.profiles.contributions.actor-user-id", "cont-actor",
                "temporary-portal-access-context.profiles.contributions.policy-no", "cont-policyNo"
        );

        var bound = bind(props);

        assertEquals(2, bound.getProfiles().size());
        assertEquals("notif-actor", bound.getProfiles().get("notifications").getActorUserId());
        assertEquals("notif-policyNo", bound.getProfiles().get("notifications").getPolicyNo());
        assertEquals("cont-actor", bound.getProfiles().get("contributions").getActorUserId());
        assertEquals("cont-policyNo", bound.getProfiles().get("contributions").getPolicyNo());
    }

    @Test
    void optionalFieldsDefaultToEmptyString() {
        var props = Map.of(
                "temporary-portal-access-context.profiles.notifications.actor-user-id", "actor",
                "temporary-portal-access-context.profiles.notifications.policy-no", "policyNo"
        );

        var bound = bind(props);
        var profile = bound.getProfiles().get("notifications");

        assertNotNull(profile);
        assertEquals("", profile.getActorUserType());
        assertEquals("", profile.getActorUserRole());
        assertEquals("", profile.getMemberUserId());
        assertEquals("", profile.getMemberType());
        assertEquals("", profile.getAccountEnv());
        assertEquals("", profile.getCertNo());
        assertEquals("", profile.getTrustCode());
        assertEquals("", profile.getSchemeType());
        assertEquals("", profile.getTermStatus());
        assertEquals("", profile.getTermCompletionDate());
    }

    private static TemporaryPortalAccessContextProperties bind(Map<String, String> props) {
        var binder = new Binder(new MapConfigurationPropertySource(props));
        return binder.bind("temporary-portal-access-context",
                        Bindable.of(TemporaryPortalAccessContextProperties.class))
                .orElseThrow(() -> new IllegalStateException(
                        "Failed to bind TemporaryPortalAccessContextProperties"));
    }

    @Test
    void bindsSessionShapedTemporaryPortalAccessContext() {
        var props = Map.ofEntries(
                Map.entry("temporary-portal-access-context.default-session-id", "SESSION-001"),
                Map.entry("temporary-portal-access-context.sessions.SESSION-001.actor.actor-user-id", "actor-session-001"),
                Map.entry("temporary-portal-access-context.sessions.SESSION-001.actor.actor-user-role", "MEMBER"),
                Map.entry("temporary-portal-access-context.sessions.SESSION-001.accounts.ACC-1.account-env", "JP"),
                Map.entry("temporary-portal-access-context.sessions.SESSION-001.accounts.ACC-1.policy-no", "00000000217"),
                Map.entry("temporary-portal-access-context.sessions.SESSION-001.accounts.ACC-1.cert-no", "95"),
                Map.entry("temporary-portal-access-context.sessions.SESSION-001.accounts.ACC-1.trust-code", "JPM"),
                Map.entry("temporary-portal-access-context.sessions.SESSION-001.accounts.ACC-1.scheme-type", "OE"),
                Map.entry("temporary-portal-access-context.sessions.SESSION-001.accounts.ACC-1.term-status", "O"),
                Map.entry("temporary-portal-access-context.sessions.SESSION-001.accounts.ACC-1.term-completion-date", "31/03/2026"),
                Map.entry("temporary-portal-access-context.sessions.SESSION-001.accounts.ACC-2.account-env", "DB"),
                Map.entry("temporary-portal-access-context.sessions.SESSION-001.accounts.ACC-2.policy-no", "00000008802"),
                Map.entry("temporary-portal-access-context.sessions.SESSION-001.accounts.ACC-2.cert-no", "2")
        );

        var bound = bind(props);
        var session = bound.getSessions().get("SESSION-001");

        assertEquals("SESSION-001", bound.getDefaultSessionId());
        assertNotNull(session, "SESSION-001 must be bound");
        assertEquals("actor-session-001", session.getActor().getActorUserId());
        assertEquals("MEMBER", session.getActor().getActorUserRole());
        assertEquals(2, session.getAccounts().size());
        assertEquals("JP", session.getAccounts().get("ACC-1").getAccountEnv());
        assertEquals("00000000217", session.getAccounts().get("ACC-1").getPolicyNo());
        assertEquals("95", session.getAccounts().get("ACC-1").getCertNo());
        assertEquals("JPM", session.getAccounts().get("ACC-1").getTrustCode());
        assertEquals("OE", session.getAccounts().get("ACC-1").getSchemeType());
        assertEquals("O", session.getAccounts().get("ACC-1").getTermStatus());
        assertEquals("31/03/2026", session.getAccounts().get("ACC-1").getTermCompletionDate());
        assertEquals("DB", session.getAccounts().get("ACC-2").getAccountEnv());
    }

    @Test
    void sessionAccountOptionalFieldsDefaultToEmptyString() {
        var props = Map.of(
                "temporary-portal-access-context.sessions.SESSION-001.actor.actor-user-id", "actor",
                "temporary-portal-access-context.sessions.SESSION-001.accounts.ACC-1.policy-no", "policyNo"
        );

        var bound = bind(props);
        var account = bound.getSessions().get("SESSION-001").getAccounts().get("ACC-1");

        assertNotNull(account);
        assertEquals("", bound.getDefaultSessionId());
        assertEquals("", bound.getSessions().get("SESSION-001").getActor().getActorUserRole());
        assertEquals("", account.getAccountEnv());
        assertEquals("policyNo", account.getPolicyNo());
        assertEquals("", account.getCertNo());
        assertEquals("", account.getTrustCode());
        assertEquals("", account.getSchemeType());
        assertEquals("", account.getTermStatus());
        assertEquals("", account.getTermCompletionDate());
    }

}
