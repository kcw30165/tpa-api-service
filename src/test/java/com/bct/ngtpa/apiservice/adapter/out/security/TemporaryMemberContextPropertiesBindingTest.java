package com.bct.ngtpa.apiservice.adapter.out.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TemporaryMemberContextPropertiesBindingTest {

    @Test
    void bindsNotificationsProfile() {
        var props = Map.of(
                "temporary-member-context.profiles.notifications.policy-no", "policyNo_for_notifications",
                "temporary-member-context.profiles.notifications.cert-no", "certNo_for_notifications",
                "temporary-member-context.profiles.notifications.user-id", "userId_for_notifications",
                "temporary-member-context.profiles.notifications.trust-code", "trustCode_for_notifications",
                "temporary-member-context.profiles.notifications.scheme-type", "schemeType_for_notifications"
        );

        var bound = bind(props);
        var profile = bound.getProfiles().get("notifications");

        assertNotNull(profile, "notifications profile must be present");
        assertEquals("policyNo_for_notifications", profile.getPolicyNo());
        assertEquals("certNo_for_notifications", profile.getCertNo());
        assertEquals("userId_for_notifications", profile.getUserId());
        assertEquals("trustCode_for_notifications", profile.getTrustCode());
        assertEquals("schemeType_for_notifications", profile.getSchemeType());
    }

    @Test
    void bindsContributionsProfile() {
        var props = Map.of(
                "temporary-member-context.profiles.contributions.policy-no", "policyNo_for_contributions",
                "temporary-member-context.profiles.contributions.cert-no", "certNo_for_contributions",
                "temporary-member-context.profiles.contributions.user-id", "userId_for_contributions",
                "temporary-member-context.profiles.contributions.trust-code", "trustCode_for_contributions",
                "temporary-member-context.profiles.contributions.scheme-type", "schemeType_for_contributions"
        );

        var bound = bind(props);
        var profile = bound.getProfiles().get("contributions");

        assertNotNull(profile, "contributions profile must be present");
        assertEquals("policyNo_for_contributions", profile.getPolicyNo());
        assertEquals("certNo_for_contributions", profile.getCertNo());
        assertEquals("userId_for_contributions", profile.getUserId());
        assertEquals("trustCode_for_contributions", profile.getTrustCode());
        assertEquals("schemeType_for_contributions", profile.getSchemeType());
    }

    @Test
    void bindsBothProfilesTogether() {
        var props = Map.of(
                "temporary-member-context.profiles.notifications.policy-no", "notif-policyNo",
                "temporary-member-context.profiles.notifications.cert-no", "notif-certNo",
                "temporary-member-context.profiles.notifications.user-id", "notif-userId",
                "temporary-member-context.profiles.contributions.policy-no", "cont-policyNo",
                "temporary-member-context.profiles.contributions.cert-no", "cont-certNo",
                "temporary-member-context.profiles.contributions.user-id", "cont-userId"
        );

        var bound = bind(props);

        assertEquals(2, bound.getProfiles().size());
        assertEquals("notif-policyNo", bound.getProfiles().get("notifications").getPolicyNo());
        assertEquals("cont-policyNo", bound.getProfiles().get("contributions").getPolicyNo());
    }

    @Test
    void optionalFieldsDefaultToEmptyString() {
        var props = Map.of(
                "temporary-member-context.profiles.notifications.policy-no", "policyNo",
                "temporary-member-context.profiles.notifications.cert-no", "certNo",
                "temporary-member-context.profiles.notifications.user-id", "userId"
        );

        var bound = bind(props);
        var profile = bound.getProfiles().get("notifications");

        assertNotNull(profile);
        assertEquals("", profile.getTrustCode());
        assertEquals("", profile.getSchemeType());
    }

    private static TemporaryMemberContextProperties bind(Map<String, String> props) {
        var binder = new Binder(new MapConfigurationPropertySource(props));
        return binder.bind("temporary-member-context", Bindable.of(TemporaryMemberContextProperties.class))
                .orElseThrow(() -> new IllegalStateException("Failed to bind TemporaryMemberContextProperties"));
    }
}
