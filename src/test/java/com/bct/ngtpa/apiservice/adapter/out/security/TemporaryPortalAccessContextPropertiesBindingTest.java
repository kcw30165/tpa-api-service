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
        var props = Map.of(
                "temporary-portal-access-context.profiles.notifications.actor-user-id", "actor_user_notifications",
                "temporary-portal-access-context.profiles.notifications.actor-user-type", "MEMBER",
                "temporary-portal-access-context.profiles.notifications.actor-user-role", "SELF",
                "temporary-portal-access-context.profiles.notifications.member-user-id", "member_user_notifications",
                "temporary-portal-access-context.profiles.notifications.member-type", "MBR",
                "temporary-portal-access-context.profiles.notifications.account-env", "JP",
                "temporary-portal-access-context.profiles.notifications.policy-no", "policyNo_for_notifications",
                "temporary-portal-access-context.profiles.notifications.cert-no", "certNo_for_notifications",
                "temporary-portal-access-context.profiles.notifications.trust-code", "trustCode_for_notifications",
                "temporary-portal-access-context.profiles.notifications.scheme-type", "schemeType_for_notifications"
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
    }

    @Test
    void bindsAllFieldsForContributionsProfile() {
        var props = Map.of(
                "temporary-portal-access-context.profiles.contributions.actor-user-id", "actor_user_contributions",
                "temporary-portal-access-context.profiles.contributions.actor-user-type", "MEMBER",
                "temporary-portal-access-context.profiles.contributions.actor-user-role", "SELF",
                "temporary-portal-access-context.profiles.contributions.member-user-id", "member_user_contributions",
                "temporary-portal-access-context.profiles.contributions.member-type", "MBR",
                "temporary-portal-access-context.profiles.contributions.account-env", "JP",
                "temporary-portal-access-context.profiles.contributions.policy-no", "policyNo_for_contributions",
                "temporary-portal-access-context.profiles.contributions.cert-no", "certNo_for_contributions",
                "temporary-portal-access-context.profiles.contributions.trust-code", "trustCode_for_contributions",
                "temporary-portal-access-context.profiles.contributions.scheme-type", "schemeType_for_contributions"
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
    }

    private static TemporaryPortalAccessContextProperties bind(Map<String, String> props) {
        var binder = new Binder(new MapConfigurationPropertySource(props));
        return binder.bind("temporary-portal-access-context",
                        Bindable.of(TemporaryPortalAccessContextProperties.class))
                .orElseThrow(() -> new IllegalStateException(
                        "Failed to bind TemporaryPortalAccessContextProperties"));
    }
}
