package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.PersonalInformationUpdateAccountResponse;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class PersonalInformationUpdateResultArrayArchitectureGuardTest {

    @Test
    void updateResponseMapperReturnsMutationResponseOfAccountResponseList() throws Exception {
        Type returnType = PersonalInformationUpdateResponseMapper.class
                .getMethod("toResponse", UpdatePersonalInformationResult.class)
                .getGenericReturnType();

        assertThat(returnType).isInstanceOf(ParameterizedType.class);
        ParameterizedType mutationResponseType = (ParameterizedType) returnType;
        assertThat(mutationResponseType.getRawType()).isEqualTo(MutationResponse.class);

        Type resultType = mutationResponseType.getActualTypeArguments()[0];
        assertThat(resultType).isInstanceOf(ParameterizedType.class);
        ParameterizedType listType = (ParameterizedType) resultType;
        assertThat(listType.getRawType()).isEqualTo(List.class);
        assertThat(listType.getActualTypeArguments()[0]).isEqualTo(PersonalInformationUpdateAccountResponse.class);
    }

    @Test
    void removedWrapperResultDtoIsNotReintroducedInMainSources() throws Exception {
        Path sourceRoot = Path.of("src/main/java");

        List<Path> offenders;
        try (var stream = Files.walk(sourceRoot)) {
            offenders = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> contains(path, "PersonalInformationUpdateResultResponse"))
                    .toList();
        }

        assertThat(offenders)
                .as("Personal information update result must remain the account array itself; do not reintroduce the wrapper DTO")
                .isEmpty();
    }

    private boolean contains(Path path, String needle) {
        try {
            return Files.readString(path).contains(needle);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Failed to read " + path, ex);
        }
    }
}
