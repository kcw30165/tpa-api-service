package com.bct.ngtpa.apiservice.adapter.out.security;

import com.bct.ngtpa.apiservice.application.dto.ContributionActions;
import com.bct.ngtpa.apiservice.application.port.out.ContributionActionPermissionPort;
import org.springframework.stereotype.Component;

/**
 * Temporary implementation of {@link ContributionActionPermissionPort}.
 *
 * <p>Returns a fixed set of contribution actions with export enabled.
 * Replace with access-token-based permission resolution when auth is fully implemented.
 */
@Component
public class TemporaryContributionActionPermissionAdapter implements ContributionActionPermissionPort {

    @Override
    public ContributionActions resolveContributionActions() {
        return new ContributionActions(true);
    }
}
