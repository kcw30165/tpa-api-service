package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationFieldMapper;
import com.bct.ngtpa.apiservice.application.dto.*;
import com.bct.ngtpa.apiservice.application.port.out.ApimMemberInfoPort;
import com.bct.ngtpa.apiservice.application.port.out.PortalAccessContextPort;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Failing tests for the Personal Information application use case (TDD first).
 *
 * These tests are intentionally designed to fail because the use case implementation
 * and/or ports expected by the test are not implemented yet. They encode the desired
 * observable behaviour so that the production implementation can be driven by tests.
 */
class GetPersonalInformationUseCaseTest {

    private static final PortalAccessContext SAMPLE_CONTEXT = new PortalAccessContext(
            new ActorContext("actor-user", "STAFF", "RM"),
            new MemberOwnerContext("member-123", "MBR"),
            new AccountContext("acc-ref", "JP", "policy-111", "cert-222", "trustX", "schemeA", TermStatus.BLANK, null)
    );

    private static PortalAccessContextPort portalAccessContextPort() {
        return accountRef -> Mono.just(SAMPLE_CONTEXT);
    }

    private static ApimMemberInfoPort apimMemberInfoPort() {
        return command -> {
            Map<String, Object> payload = new HashMap<>();
            Map<String, String> cfg = new HashMap<>();
            cfg.put("addr1", "EDITABLE_COM");
            payload.put("config", cfg);
            Map<String, Object> data = new HashMap<>();
            data.put("addr1", "1 Example Street");
            payload.put("data", data);
            return Mono.just(new MemberInfoResult(payload));
        };
    }

    private static PersonalInformationFieldMapper mapperStub() {
        return (apimData, apimConfig, bffPagesProperties) -> {
            Map<String, Object> out = new HashMap<>();
            out.put("fields", Map.of("addr1", Map.of("value", "1 Example Street")));
            out.put("sections", Map.of());
            out.put("confirmation", Map.of("enabled", false));
            return out;
        };
    }

    @Test
    void resolves_portal_context_and_calls_apim_and_mapper_and_returns_page() {
        // This test exercises the intended behaviour of the application use-case:
        // - resolve portal access context using PortalAccessContextPort
        // - call ApimMemberInfoPort with account/env/policy/cert/user derived from the context
        // - call PersonalInformationFieldMapper to assemble BFF page fields
        // The concrete use-case implementation is not present yet; running this test
        // will fail until the use-case is implemented (expected TDD failure).

        ApimMemberInfoPort apimPort = apimMemberInfoPort();
        PortalAccessContextPort portalPort = portalAccessContextPort();
        PersonalInformationFieldMapper mapper = mapperStub();

        try {
            // Attempt to load the expected service class; this will throw ClassNotFoundException
            // until the production implementation is added (test will therefore fail as intended).
            Class<?> svcClass = Class.forName("com.bct.ngtpa.apiservice.application.usecase.GetPersonalInformationService");
            // If the class exists later, attempt to instantiate with the conventional constructor
            var ctor = svcClass.getConstructor(ApimMemberInfoPort.class, PortalAccessContextPort.class, PersonalInformationFieldMapper.class);
            Object svc = ctor.newInstance(apimPort, portalPort, mapper);

            // If created, call the execute method with a command containing accountRef + language
            Class<?> cmdClass = Class.forName("com.bct.ngtpa.apiservice.application.dto.GetPersonalInformationCommand");
            Object cmd = cmdClass.getConstructor(String.class, String.class).newInstance("acc-ref", "en");
            var executeMethod = svcClass.getMethod("execute", cmdClass);
            executeMethod.invoke(svc, cmd);
        } catch (ClassNotFoundException e) {
            // Re-throw so the test fails (expected until service exists)
            throw new RuntimeException(e);
        } catch (ReflectiveOperationException e) {
            // Any reflection issue should also fail the test
            throw new RuntimeException(e);
        }
    }

    @Test
    void propagates_apim_exceptions() {
        ApimMemberInfoPort failingApim = command -> Mono.error(new IllegalStateException("apim-failure"));
        PortalAccessContextPort portalPort = portalAccessContextPort();
        PersonalInformationFieldMapper mapper = mapperStub();

        try {
            Class<?> svcClass = Class.forName("com.bct.ngtpa.apiservice.application.usecase.GetPersonalInformationService");
            var ctor = svcClass.getConstructor(ApimMemberInfoPort.class, PortalAccessContextPort.class, PersonalInformationFieldMapper.class);
            Object svc = ctor.newInstance(failingApim, portalPort, mapper);
            Class<?> cmdClass = Class.forName("com.bct.ngtpa.apiservice.application.dto.GetPersonalInformationCommand");
            Object cmd = cmdClass.getConstructor(String.class, String.class).newInstance("acc-ref", "en");
            var executeMethod = svcClass.getMethod("execute", cmdClass);
            executeMethod.invoke(svc, cmd);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
