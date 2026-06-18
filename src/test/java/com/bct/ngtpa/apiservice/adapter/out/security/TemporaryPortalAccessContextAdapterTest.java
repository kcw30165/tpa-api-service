package com.bct.ngtpa.apiservice.adapter.out.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporaryPortalAccessContextAdapterTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(TemporaryPortalAccessContextAdapter.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(listAppender);
        listAppender.stop();
    }

    @Test
    void resolvesNotificationsProfile() {
        var adapter = adapterWithBothProfiles();

        StepVerifier.create(adapter.resolvePortalAccessContext("notifications"))
                .assertNext(ctx -> {
                    assertEquals("actorUserId_for_notifications", ctx.actor().actorUserId());
                    // assertEquals("MEMBER", ctx.actor().actorUserType());
                    assertEquals("SELF", ctx.actor().actorUserRole());
                    assertEquals("JP", ctx.account().accountEnv());
                    assertEquals("policyNo_for_notifications", ctx.account().policyNo());
                    assertEquals("certNo_for_notifications", ctx.account().certNo());
                    assertEquals("trustCode_for_notifications", ctx.account().trustCode());
                    assertEquals("schemeType_for_notifications", ctx.account().schemeType());
                    assertEquals(TermStatus.O, ctx.account().termStatus());
                    assertEquals(LocalDate.of(2026, 3, 31), ctx.account().termCompletionDate());
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
                    // assertEquals("MEMBER", ctx.actor().actorUserType());
                    assertEquals("SELF", ctx.actor().actorUserRole());
                    assertEquals("JP", ctx.account().accountEnv());
                    assertEquals("policyNo_for_contributions", ctx.account().policyNo());
                    assertEquals("certNo_for_contributions", ctx.account().certNo());
                    assertEquals("trustCode_for_contributions", ctx.account().trustCode());
                    assertEquals("schemeType_for_contributions", ctx.account().schemeType());
                    assertEquals(TermStatus.P, ctx.account().termStatus());
                    assertEquals(LocalDate.of(2026, 4, 30), ctx.account().termCompletionDate());
                    assertEquals("contributions", ctx.account().accountRef());
                })
                .verifyComplete();
    }

    @Test
    void resolvesAccountRefKeyedProfile() {
        var accountRefProfile = new TemporaryPortalAccessContextProperties.Profile();
        accountRefProfile.setActorUserId("actorUserId_for_acc_123");
        accountRefProfile.setActorUserType("MEMBER");
        accountRefProfile.setActorUserRole("SELF");
        accountRefProfile.setAccountEnv("JP");
        accountRefProfile.setPolicyNo("policyNo_for_acc_123");
        accountRefProfile.setCertNo("certNo_for_acc_123");
        accountRefProfile.setTrustCode("trustCode_for_acc_123");
        accountRefProfile.setSchemeType("schemeType_for_acc_123");
        accountRefProfile.setTermStatus("O");
        accountRefProfile.setTermCompletionDate("31/03/2026");

        var properties = new TemporaryPortalAccessContextProperties();
        properties.setProfiles(Map.of("ACC-123", accountRefProfile));

        var adapter = new TemporaryPortalAccessContextAdapter(properties);

        StepVerifier.create(adapter.resolvePortalAccessContext("ACC-123"))
                .assertNext(ctx -> {
                    assertEquals("actorUserId_for_acc_123", ctx.actor().actorUserId());
                    assertEquals("policyNo_for_acc_123", ctx.account().policyNo());
                    assertEquals("ACC-123", ctx.account().accountRef());
                })
                .verifyComplete();
    }

    @Test
    void mapsBlankRawTermStatusToBlankWithoutWarning() {
        var adapter = adapterForProfile(profile("", "JP", "31/03/2026"));

        StepVerifier.create(adapter.resolvePortalAccessContext("notifications"))
                .assertNext(ctx -> assertEquals(TermStatus.BLANK, ctx.account().termStatus()))
                .verifyComplete();

        assertEquals(0, listAppender.list.size());

        var nullAdapter = adapterForProfile(profile(null, "JP", "31/03/2026"));

        StepVerifier.create(nullAdapter.resolvePortalAccessContext("notifications"))
            .assertNext(ctx -> assertEquals(TermStatus.BLANK, ctx.account().termStatus()))
            .verifyComplete();

        assertEquals(0, listAppender.list.size());
    }

    @Test
    void mapsSupportedRawTermStatusCodes() {
        assertMappedTermStatus("O", TermStatus.O);
        assertMappedTermStatus("P", TermStatus.P);
        assertMappedTermStatus("S", TermStatus.S);
        assertMappedTermStatus("T", TermStatus.T);
    }

    @Test
    void mapsBlankTermCompletionDateToNull() {
        var adapter = adapterForProfile(profile("S", "JP", ""));

        StepVerifier.create(adapter.resolvePortalAccessContext("notifications"))
                .assertNext(ctx -> assertNull(ctx.account().termCompletionDate()))
                .verifyComplete();
    }

    @Test
    void mapsInvalidNonBlankRawTermStatusToUnknownAndWarnsOnce() {
        var adapter = adapterForProfile(profile("X", "JP", "31/03/2026"));

        StepVerifier.create(adapter.resolvePortalAccessContext("notifications"))
                .assertNext(ctx -> assertEquals(TermStatus.UNKNOWN, ctx.account().termStatus()))
                .verifyComplete();

        assertEquals(1, listAppender.list.size());
        ILoggingEvent event = listAppender.list.getFirst();
        assertEquals(Level.WARN, event.getLevel());
        assertTrue(event.getFormattedMessage().contains("source=temporary-portal-access-context"));
        assertTrue(event.getFormattedMessage().contains("accountRef=notifications"));
        assertTrue(event.getFormattedMessage().contains("accountEnv=JP"));
        assertTrue(event.getFormattedMessage().contains("rawTermStatus=X"));
        assertTrue(!event.getFormattedMessage().contains("policyNo"));
        assertTrue(!event.getFormattedMessage().contains("certNo"));
        assertTrue(!event.getFormattedMessage().contains("actorUserId"));
        assertTrue(!event.getFormattedMessage().contains("memberUserId"));
    }

    @Test
    void failsWithClearExceptionWhenProfileIsMissing() {
        var properties = new TemporaryPortalAccessContextProperties();
        properties.setProfiles(Map.of());
        var adapter = new TemporaryPortalAccessContextAdapter(properties);

        StepVerifier.create(adapter.resolvePortalAccessContext("notifications"))
                .expectErrorSatisfies(ex -> {
                    assertInstanceOf(PortalAccessContextResolutionException.class, ex);
                    assertEquals(
                            "No temporary portal access context profile configured for accountRef: notifications",
                            ex.getMessage());
                })
                .verify();
    }

    @Test
    void unknownAccountRefFailsWithClearException() {
        var properties = new TemporaryPortalAccessContextProperties();
        properties.setProfiles(Map.of("ACC-123", profile("O", "JP", "31/03/2026")));
        var adapter = new TemporaryPortalAccessContextAdapter(properties);

        StepVerifier.create(adapter.resolvePortalAccessContext("ACC-999"))
                .expectErrorSatisfies(ex -> {
                    assertInstanceOf(PortalAccessContextResolutionException.class, ex);
                    assertEquals(
                            "No temporary portal access context profile configured for accountRef: ACC-999",
                            ex.getMessage());
                })
                .verify();
    }

    @Test
    void blankAccountRefFailsWithClearException() {
        var properties = new TemporaryPortalAccessContextProperties();
        properties.setProfiles(Map.of());
        var adapter = new TemporaryPortalAccessContextAdapter(properties);

        StepVerifier.create(adapter.resolvePortalAccessContext(""))
                .expectErrorSatisfies(ex -> {
                    assertInstanceOf(PortalAccessContextResolutionException.class, ex);
                    assertEquals(
                            "Account-Ref is required to resolve portal access context.",
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
                    // assertEquals("", ctx.actor().actorUserType());
                    assertEquals("", ctx.actor().actorUserRole());
                    assertEquals("", ctx.account().accountEnv());
                    assertEquals("", ctx.account().certNo());
                    assertEquals("", ctx.account().trustCode());
                    assertEquals("", ctx.account().schemeType());
                    assertEquals(TermStatus.BLANK, ctx.account().termStatus());
                    assertNull(ctx.account().termCompletionDate());
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
        notifProfile.setAccountEnv("JP");
        notifProfile.setPolicyNo("policyNo_for_notifications");
        notifProfile.setCertNo("certNo_for_notifications");
        notifProfile.setTrustCode("trustCode_for_notifications");
        notifProfile.setSchemeType("schemeType_for_notifications");
        notifProfile.setTermStatus("O");
        notifProfile.setTermCompletionDate("31/03/2026");

        var contribProfile = new TemporaryPortalAccessContextProperties.Profile();
        contribProfile.setActorUserId("actorUserId_for_contributions");
        contribProfile.setActorUserType("MEMBER");
        contribProfile.setActorUserRole("SELF");
        contribProfile.setAccountEnv("JP");
        contribProfile.setPolicyNo("policyNo_for_contributions");
        contribProfile.setCertNo("certNo_for_contributions");
        contribProfile.setTrustCode("trustCode_for_contributions");
        contribProfile.setSchemeType("schemeType_for_contributions");
        contribProfile.setTermStatus("P");
        contribProfile.setTermCompletionDate("30/04/2026");

        var profiles = new LinkedHashMap<String, TemporaryPortalAccessContextProperties.Profile>();
        profiles.put("notifications", notifProfile);
        profiles.put("contributions", contribProfile);

        var properties = new TemporaryPortalAccessContextProperties();
        properties.setProfiles(profiles);

        return new TemporaryPortalAccessContextAdapter(properties);
    }

    private void assertMappedTermStatus(String rawStatus, TermStatus expected) {
        listAppender.list.clear();
        var adapter = adapterForProfile(profile(rawStatus, "JP", "31/03/2026"));

        StepVerifier.create(adapter.resolvePortalAccessContext("notifications"))
                .assertNext(ctx -> assertEquals(expected, ctx.account().termStatus()))
                .verifyComplete();

        assertEquals(0, listAppender.list.size());
    }

    private static TemporaryPortalAccessContextAdapter adapterForProfile(TemporaryPortalAccessContextProperties.Profile profile) {
        var properties = new TemporaryPortalAccessContextProperties();
        properties.setProfiles(Map.of("notifications", profile));
        return new TemporaryPortalAccessContextAdapter(properties);
    }

    private static TemporaryPortalAccessContextProperties.Profile profile(String rawTermStatus, String accountEnv, String termCompletionDate) {
        var profile = new TemporaryPortalAccessContextProperties.Profile();
        profile.setActorUserId("actorUserId");
        profile.setActorUserType("MEMBER");
        profile.setActorUserRole("SELF");
        profile.setAccountEnv(accountEnv);
        profile.setPolicyNo("policyNo_secret");
        profile.setCertNo("certNo_secret");
        profile.setTrustCode("trustCode");
        profile.setSchemeType("schemeType");
        profile.setTermStatus(rawTermStatus);
        profile.setTermCompletionDate(termCompletionDate);
        return profile;
    }

    @Test
    void resolvesSelectedAccountFromDefaultSessionShape() {
        var adapter = adapterWithSessionShape();

        StepVerifier.create(adapter.resolvePortalAccessContext("ACC-1", "SESSION-001"))
                .assertNext(ctx -> {
                    assertEquals("actorUserId_for_session", ctx.actor().actorUserId());
                    assertEquals("MEMBER", ctx.actor().actorUserRole());
                    assertEquals("ACC-1", ctx.account().accountRef());
                    assertEquals("JP", ctx.account().accountEnv());
                    assertEquals("00000000217", ctx.account().policyNo());
                    assertEquals("95", ctx.account().certNo());
                    assertEquals("JPM", ctx.account().trustCode());
                    assertEquals("OE", ctx.account().schemeType());
                    assertEquals(TermStatus.O, ctx.account().termStatus());
                    assertEquals(LocalDate.of(2026, 3, 31), ctx.account().termCompletionDate());
                })
                .verifyComplete();
    }

    @Test
    void sameSessionResolvesDifferentAccountRefsWithoutSelectedAccountState() {
        var adapter = adapterWithSessionShape();

        StepVerifier.create(adapter.resolvePortalAccessContext("ACC-2", "SESSION-001"))
                .assertNext(ctx -> {
                    assertEquals("actorUserId_for_session", ctx.actor().actorUserId());
                    assertEquals("ACC-2", ctx.account().accountRef());
                    assertEquals("DB", ctx.account().accountEnv());
                    assertEquals("00000008802", ctx.account().policyNo());
                    assertEquals("2", ctx.account().certNo());
                    assertEquals("", ctx.account().trustCode());
                    assertEquals("", ctx.account().schemeType());
                })
                .verifyComplete();
    }

    @Test
    void sessionShapeUnknownAccountRefFailsWithClearException() {
        var adapter = adapterWithSessionShape();

        StepVerifier.create(adapter.resolvePortalAccessContext("ACC-404", "SESSION-001"))
                .expectErrorSatisfies(ex -> {
                    assertInstanceOf(PortalAccessContextResolutionException.class, ex);
                    assertEquals("No temporary portal access context account configured for accountRef: ACC-404 in session: SESSION-001",
                            ex.getMessage());
                })
                .verify();
    }

    private static TemporaryPortalAccessContextAdapter adapterWithSessionShape() {
        var actor = new TemporaryPortalAccessContextProperties.SessionActor();
        actor.setActorUserId("actorUserId_for_session");
        actor.setActorUserRole("MEMBER");

        var acc1 = new TemporaryPortalAccessContextProperties.AccountProfile();
        acc1.setAccountEnv("JP");
        acc1.setPolicyNo("00000000217");
        acc1.setCertNo("95");
        acc1.setTrustCode("JPM");
        acc1.setSchemeType("OE");
        acc1.setTermStatus("O");
        acc1.setTermCompletionDate("31/03/2026");

        var acc2 = new TemporaryPortalAccessContextProperties.AccountProfile();
        acc2.setAccountEnv("DB");
        acc2.setPolicyNo("00000008802");
        acc2.setCertNo("2");

        var session = new TemporaryPortalAccessContextProperties.SessionProfile();
        session.setActor(actor);
        var accounts = new LinkedHashMap<String, TemporaryPortalAccessContextProperties.AccountProfile>();
        accounts.put("ACC-1", acc1);
        accounts.put("ACC-2", acc2);
        session.setAccounts(accounts);

        var properties = new TemporaryPortalAccessContextProperties();
        properties.setDefaultSessionId("SESSION-001");
        properties.setSessions(Map.of("SESSION-001", session));
        return new TemporaryPortalAccessContextAdapter(properties);
    }

}
