package com.bct.ngtpa.apiservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryLookupCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryLookupResult;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class UsernameRecoveryLookupPortTest {

    @Test
    void usernameRecoveryLookupPortExposesSearchOperation() throws Exception {
        Method method = UsernameRecoveryLookupPort.class.getMethod("search",
                UsernameRecoveryLookupCommand.class);

        assertThat(method.getReturnType()).isEqualTo(Mono.class);
        assertThat(method.getGenericReturnType().getTypeName())
                .isEqualTo(Mono.class.getName() + "<" + UsernameRecoveryLookupResult.class.getName() + ">");
    }
}
