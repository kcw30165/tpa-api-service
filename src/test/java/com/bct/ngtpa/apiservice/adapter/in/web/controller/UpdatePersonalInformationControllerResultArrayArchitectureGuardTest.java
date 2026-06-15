package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.PersonalInformationUpdateResultResponse;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class UpdatePersonalInformationControllerResultArrayArchitectureGuardTest {

    @Test
    void updateEndpointReturnsMonoOfMutationResponseOfAccountResponseList() {
        Type returnType = Arrays.stream(UpdatePersonalInformationController.class.getDeclaredMethods())
                .filter(method -> "update".equals(method.getName()))
                .findFirst()
                .orElseThrow()
                .getGenericReturnType();

        assertThat(returnType).isInstanceOf(ParameterizedType.class);
        ParameterizedType monoType = (ParameterizedType) returnType;
        assertThat(monoType.getRawType()).isEqualTo(Mono.class);

        Type mutationTypeArgument = monoType.getActualTypeArguments()[0];
        assertThat(mutationTypeArgument).isInstanceOf(ParameterizedType.class);
        ParameterizedType mutationType = (ParameterizedType) mutationTypeArgument;
        assertThat(mutationType.getRawType()).isEqualTo(MutationResponse.class);

        Type resultType = mutationType.getActualTypeArguments()[0];
        assertThat(resultType).isInstanceOf(ParameterizedType.class);
        ParameterizedType listType = (ParameterizedType) resultType;
        assertThat(listType.getRawType()).isEqualTo(List.class);
        assertThat(listType.getActualTypeArguments()[0]).isEqualTo(PersonalInformationUpdateResultResponse.class);
    }
}
