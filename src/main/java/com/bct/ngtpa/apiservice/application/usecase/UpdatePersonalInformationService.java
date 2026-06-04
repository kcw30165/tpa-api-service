package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.UpdateMemberInfoCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import com.bct.ngtpa.apiservice.application.port.in.UpdatePersonalInformationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimUpdatePersonalInformationPort;
import com.bct.ngtpa.apiservice.application.port.out.PortalAccessContextPort;
import java.util.Objects;
import reactor.core.publisher.Mono;

public class UpdatePersonalInformationService implements UpdatePersonalInformationUseCase {

    private final ApimUpdatePersonalInformationPort apimUpdatePersonalInformationPort;
    private final PortalAccessContextPort portalAccessContextPort;

    public UpdatePersonalInformationService(
            ApimUpdatePersonalInformationPort apimUpdatePersonalInformationPort,
            PortalAccessContextPort portalAccessContextPort) {
        this.apimUpdatePersonalInformationPort = Objects.requireNonNull(apimUpdatePersonalInformationPort);
        this.portalAccessContextPort = Objects.requireNonNull(portalAccessContextPort);
    }

    @Override
    public Mono<UpdatePersonalInformationResult> execute(UpdatePersonalInformationCommand command) {
        return portalAccessContextPort.resolvePortalAccessContext(command.accountRef())
                .flatMap(context -> apimUpdatePersonalInformationPort.updateMemberInfo(new UpdateMemberInfoCommand(
                        context.account().accountEnv(),
                        context.account().policyNo(),
                        context.account().certNo(),
                        context.actor().actorUserId(),
                        command.updateFields())));
    }
}
