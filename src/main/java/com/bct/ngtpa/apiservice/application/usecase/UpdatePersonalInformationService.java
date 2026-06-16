package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.UpdateMemberInfoCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
import com.bct.ngtpa.apiservice.application.port.in.UpdatePersonalInformationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimUpdatePersonalInformationPort;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextProvider;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

public class UpdatePersonalInformationService implements UpdatePersonalInformationUseCase {

    private final ApimUpdatePersonalInformationPort apimUpdatePersonalInformationPort;
    private final CurrentPortalAccessContextProvider currentPortalAccessContextProvider;

    public UpdatePersonalInformationService(
            ApimUpdatePersonalInformationPort apimUpdatePersonalInformationPort,
            CurrentPortalAccessContextProvider currentPortalAccessContextProvider) {
        this.apimUpdatePersonalInformationPort = Objects.requireNonNull(apimUpdatePersonalInformationPort);
        this.currentPortalAccessContextProvider = Objects.requireNonNull(currentPortalAccessContextProvider);
    }

    public UpdatePersonalInformationService(
            ApimUpdatePersonalInformationPort apimUpdatePersonalInformationPort,
            Object ignoredLegacyContextDependency) {
        this.apimUpdatePersonalInformationPort = Objects.requireNonNull(apimUpdatePersonalInformationPort);
        this.currentPortalAccessContextProvider = null;
    }

    @Override
    public Mono<List<UpdatePersonalInformationResult>> execute(UpdatePersonalInformationCommand command) {
        return currentPortalAccessContext()
                .flatMap(context -> apimUpdatePersonalInformationPort.updateMemberInfo(new UpdateMemberInfoCommand(
                        context.account().accountEnv(),
                        context.account().policyNo(),
                        context.account().certNo(),
                        context.actor().actorUserId(),
                        context.actor().actorUserRole(),
                        command.applyToAllAccounts(),
                        command.updateFields())));
    }

    private Mono<com.bct.ngtpa.apiservice.application.dto.PortalAccessContext> currentPortalAccessContext() {
        if (currentPortalAccessContextProvider == null) {
            return Mono.error(new PortalAccessContextResolutionException(
                    ErrorCodes.MEMBER_CONTEXT_INVALID,
                    "PortalAccessContext is not available in the current request context."));
        }
        return currentPortalAccessContextProvider.current();
    }
}
