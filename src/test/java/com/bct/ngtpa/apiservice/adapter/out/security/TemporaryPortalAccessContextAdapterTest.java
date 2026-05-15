package com.bct.ngtpa.apiservice.adapter.out.security;

import com.bct.ngtpa.apiservice.application.exception.MemberContextResolutionException;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class TemporaryPortalAccessContextAdapterTest {

    @Test
    void resolvesNotificationsProfile() {
        var adapter = adapterWithBothProfiles();

        StepVerifier.create(adapter.resolvePortalAccessContext("notifications"))
                .assertNext(ctx -> {
                    assertEquals("actorUserId_for_notifications", ctx.actor().actorUserId());
                    assertEquals("MEMBER", ctx.actor().actorUserType());
                    assertEquals("SELF", ctx.actor().actorUserRole());
                    assertEquals("memberUserId_for_notifications", ctx.memberOwner().memberUserId());
                    assertEquals("INDIVIDUAL", ctx.memberOwner().memberType());
                    assertEquals("JP", ctx.account().accountEnv());
                    assertEquals("policyNo_for_notifications", ctx.account().policyNo());
                    assertEquals("certNo_for_notifications", ctx.account().certNo());
                    assertEquals("trustCode_for_notifications", ctx.account().trustCode());
                    assertEquals("schemeType_for_notifications", ctx.account().schemeType());
                    assertEquals("notifications", ctx.account().accountRef());
                })
                .verifyComplete();
    }

    @Test
    void resolvesContributionsProfile() {
        var adapter = adapterWithBothProfiles();

        StepVerifier.create(adapter.resolvePortalAccessContext("contributions"))
                .assertNext(ctx -> {
                    assertEquals("actorUserId_for_contributions", ctx.actor().actorUserId());
                    assertEquals("MEMBER", ctx.actor().actorUserType());
                    assertEquals("SELF", ctx.actor().actorUserRole());
                    assertEquals("memberUserId_for_contributions", ctx.memberOwner().memberUserId());
                    assertEquals("INDIVIDUAL", ctx.memberOwner().memberType());
                    assertEquals("JP", ctx.account().accountEnv());
                    assertEquals("policyNo_for_contributions", ctx.account().policyNo());
                    assertEquals("certNo_for_contributions", ctx.account().certNo());
                    assertEquals("trustCode_for_contributions", ctx.account().trustCode());
                    assertEquals("schemeType_for_contributions", ctx.account().schemeType());
                    assertEquals("contributions", ctx.account().accountRef());
                })
                .verifyComplete();
    }

    @Test
    void failsWithClearExceptionWhenProfileIsMissing() {
        var properties = new TemporaryPortalAccessContextProperties();
        properties.setProfiles(Map.of());
        var adapter = new TemporaryPortalAccessContextAdapter(properties);

        StepVerifier.create(adapter.resolvePortalAccessContext("notifications"))
                .expectErrorSatisfies(ex -> {
                    assertInstanceOf(MemberContextResolutionException.class, ex);
                    assertEquals(
                            "No temporary portal access context profile configured for accountRef: notifications",
                            ex.getMessage());
                })
                .verify();
    }

    @Test
    void blankOptionalFieldsDefaultToEmptyString() {
        var profile = new TemporaryPortalAccessContextProperties.Profile();
        profile.setActorUserId("actorUserId");
        profile.setPolicyNo("policyNo");
        // all other fields left as defaults

        var properties = new TemporaryPortalAccessContextProperties();
        properties.setProfiles(Map.of("notifications", profile));
        var adapter = new TemporaryPortalAccessContextAdapter(properties);

        StepVerifier.create(adapter.resolvePortalAccessContext("notifications"))
                .assertNext(ctx -> {
                    assertEquals("", ctx.actor().actorUserType());
                    assertEquals("", ctx.actor().actorUserRole());
                    assertEquals("", ctx.memberOwner().memberUserId());
                    assertEquals("", ctx.memberOwner().memberType());
                    assertEquals("", ctx.account().accountEnv());
                    assertEquals("", ctx.account().certNo());
                    assertEquals("", ctx.account().trustCode());
                    assertEquals("", ctx.account().schemeType());
                })
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private static TemporaryPortalAccessContextAdapter adapterWithBothProfiles() {
        var notifProfile = new TemporaryPortalAccessContextProperties.Profile();
        notifProfile.setActorUserId("actorUserId_for_notifications");
        notifProfile.setActorUserType("MEMBER");
        notifProfile.setActorUserRole("SELF");
        notifProfile.setMemberUserId("memberUserId_for_notifications");
        notifProfile.setMemberType("INDIVIDUAL");
        notifProfile.setAccountEnv("JP");
        notifProfile.setPolicyNo("policyNo_for_notifications");
        notifProfile.setCertNo("certNo_for_notifications");
        notifProfile.setTrustCode("trustCode_for_notifications");
        notifProfile.setSchemeType("schemeType_for_notifications");

        var contribProfile = new TemporaryPortalAccessContextProperties.Profile();
        contribProfile.setActorUserId("actorUserId_for_contributions");
        contribProfile.setActorUserType("MEMBER");
        contribProfile.setActorUserRole("SELF");
        contribProfile.setMemberUserId("memberUserId_for_contributions");
        contribProfile.setMemberType("INDIVIDUAL");
        contribProfile.setAccountEnv("JP");
        contribProfile.setPolicyNo("policyNo_for_contributions");
        contribProfile.setCertNo("certNo_for_contributions");
        contribProfile.setTrustCode("trustCode_for_contributions");
        contribProfile.setSchemeType("schemeType_for_contributions");

        var profiles = new LinkedHashMap<String, TemporaryPortalAccessContextProperties.Profile>();
        profiles.put("notifications", notifProfile);
        profiles.put("contributions", contribProfile);

        var properties = new TemporaryPortalAccessContextProperties();
        properties.setProfiles(profiles);

        return new TemporaryPortalAccessContextAdapter(properties);
    }
}
