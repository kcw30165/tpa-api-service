package com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery;
import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
class TrpSearchMemberAdapterArchitectureTest {
 @Test void operationUsesGenericExecutorAndNoFeatureTransport() throws Exception {String s=Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/usernamerecovery/TrpSearchMemberAdapter.java")); assertThat(s).contains("ApimExchangeExecutor","ApimDatabaseScope.NON_ACCOUNT","SAFE_BUSINESS_CALL").doesNotContain("TrpSearchMemberTransport","ApimWebClientFacade","ApimPayloadCryptoService");}
}
