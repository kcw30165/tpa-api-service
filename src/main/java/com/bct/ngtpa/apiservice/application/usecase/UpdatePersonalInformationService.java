package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.UpdateMemberInfoCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import com.bct.ngtpa.apiservice.application.port.in.UpdatePersonalInformationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimUpdatePersonalInformationPort;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

public class UpdatePersonalInformationService implements UpdatePersonalInformationUseCase {

    private final ApimUpdatePersonalInformationPort apimUpdatePersonalInformationPort;
    private final CurrentPortalAccessContextResolver currentPortalAccessContextResolver;

    public UpdatePersonalInformationService(
            ApimUpdatePersonalInformationPort apimUpdatePersonalInformationPort,
            CurrentPortalAccessContextResolver currentPortalAccessContextResolver) {
        this.apimUpdatePersonalInformationPort = Objects.requireNonNull(apimUpdatePersonalInformationPort);
        this.currentPortalAccessContextResolver = Objects.requireNonNull(currentPortalAccessContextResolver);
    }

    @Override
    public Mono<List<UpdatePersonalInformationResult>> execute(UpdatePersonalInformationCommand command) {
        return currentPortalAccessContextResolver.current()
                .flatMap(context -> apimUpdatePersonalInformationPort.updateMemberInfo(new UpdateMemberInfoCommand(
                        context.account().accountEnv(),
                        context.account().policyNo(),
                        context.account().certNo(),
                        context.actor().actorUserId(),
                        context.actor().actorUserRole(),
                        command.applyToAllAccounts(),
                        command.updateFields())));
    }
}
