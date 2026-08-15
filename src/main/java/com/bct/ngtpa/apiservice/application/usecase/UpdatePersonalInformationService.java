package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.UpdateMemberInfoCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import com.bct.ngtpa.apiservice.application.port.in.UpdatePersonalInformationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimUpdatePersonalInformationPort;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

public class UpdatePersonalInformationService implements UpdatePersonalInformationUseCase {
    private final ApimUpdatePersonalInformationPort apimUpdatePersonalInformationPort;
    public UpdatePersonalInformationService(ApimUpdatePersonalInformationPort port) {
        this.apimUpdatePersonalInformationPort=Objects.requireNonNull(port);
    }
    @Override public Mono<List<UpdatePersonalInformationResult>> execute(UpdatePersonalInformationCommand command) {
        return apimUpdatePersonalInformationPort.updateMemberInfo(new UpdateMemberInfoCommand(
                command.accountEnv(),command.policyNo(),command.certNo(),command.actorUserId(),command.actorUserRole(),
                command.applyToAllAccounts(),command.updateFields()));
    }
}
