package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.port.out.ApimReferenceDateRefreshPort;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import com.bct.ngtpa.apiservice.application.port.out.ConfigServicePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDateCacheUpdatePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDateConfigPort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReferenceDateUseCaseBoundaryTest {

    @Test
    void businessUseCasesDependOnReferenceDatePortAndNotReferenceDateSourceImplementationPorts() {
        assertUsesReferenceDatePortOnlyForReferenceDateResolution(GetNotificationsService.class);
        assertUsesReferenceDatePortOnlyForReferenceDateResolution(UpdateNotificationsReadStatusService.class);
        assertUsesReferenceDatePortOnlyForReferenceDateResolution(GetContributionSummaryService.class);
        assertUsesReferenceDatePortOnlyForReferenceDateResolution(ExportContributionSummaryService.class);
    }

    private void assertUsesReferenceDatePortOnlyForReferenceDateResolution(Class<?> useCaseClass) {
        Field[] fields = useCaseClass.getDeclaredFields();

        assertTrue(Arrays.stream(fields).anyMatch(field -> field.getType().equals(ReferenceDatePort.class)),
                () -> useCaseClass.getSimpleName() + " should depend on ReferenceDatePort");

        assertFalse(Arrays.stream(fields).anyMatch(field -> field.getType().equals(CachePort.class)),
                () -> useCaseClass.getSimpleName() + " must not depend on CachePort directly");
        assertFalse(Arrays.stream(fields).anyMatch(field -> field.getType().equals(ConfigServicePort.class)),
                () -> useCaseClass.getSimpleName() + " must not depend on ConfigServicePort directly");
        assertFalse(Arrays.stream(fields).anyMatch(field -> field.getType().equals(ReferenceDateConfigPort.class)),
                () -> useCaseClass.getSimpleName() + " must not depend on ReferenceDateConfigPort directly");
        assertFalse(Arrays.stream(fields).anyMatch(field -> field.getType().equals(ReferenceDateCacheUpdatePort.class)),
                () -> useCaseClass.getSimpleName() + " must not depend on ReferenceDateCacheUpdatePort directly");
        assertFalse(Arrays.stream(fields).anyMatch(field -> field.getType().equals(ApimReferenceDateRefreshPort.class)),
                () -> useCaseClass.getSimpleName() + " must not depend on ApimReferenceDateRefreshPort directly");
    }
}