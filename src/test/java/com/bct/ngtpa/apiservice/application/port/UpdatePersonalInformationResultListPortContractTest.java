package com.bct.ngtpa.apiservice.application.port;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.UpdateMemberInfoCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import com.bct.ngtpa.apiservice.application.port.in.UpdatePersonalInformationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimUpdatePersonalInformationPort;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class UpdatePersonalInformationResultListPortContractTest {

    @Test
    void apimUpdatePortReturnsMonoOfUpdateResultList() throws Exception {
        Type returnType = ApimUpdatePersonalInformationPort.class
                .getMethod("updateMemberInfo", UpdateMemberInfoCommand.class)
                .getGenericReturnType();

        assertMonoOfListOfUpdatePersonalInformationResult(returnType);
    }

    @Test
    void updateUseCaseReturnsMonoOfUpdateResultList() throws Exception {
        Type returnType = UpdatePersonalInformationUseCase.class
                .getMethod("execute", UpdatePersonalInformationCommand.class)
                .getGenericReturnType();

        assertMonoOfListOfUpdatePersonalInformationResult(returnType);
    }

    private void assertMonoOfListOfUpdatePersonalInformationResult(Type returnType) {
        assertThat(returnType).isInstanceOf(ParameterizedType.class);
        ParameterizedType monoType = (ParameterizedType) returnType;
        assertThat(monoType.getRawType()).isEqualTo(Mono.class);

        Type monoArgument = monoType.getActualTypeArguments()[0];
        assertThat(monoArgument).isInstanceOf(ParameterizedType.class);
        ParameterizedType listType = (ParameterizedType) monoArgument;
        assertThat(listType.getRawType()).isEqualTo(List.class);
        assertThat(listType.getActualTypeArguments()[0]).isEqualTo(UpdatePersonalInformationResult.class);
    }
}
