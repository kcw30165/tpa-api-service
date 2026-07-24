package com.bct.ngtpa.apiservice.architecture;
import static org.assertj.core.api.Assertions.assertThat;import java.nio.file.*;import org.junit.jupiter.api.Test;
class Task0043LoginOtpWebPackageOwnershipTest {
 @Test void loginHasNoOtpSpecificInboundWebPackage(){assertThat(Files.exists(Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/login/otp"))).isFalse();}
 @Test void loginOtpWebContractsFollowRegistrationDtoMapperOwnership() throws Exception {String dto=Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/login/dto/LoginOtpVerifyRequest.java"));String mapper=Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/login/mapper/LoginOtpVerifyWebMapper.java"));assertThat(dto).contains("adapter.in.web.auth.login.dto").doesNotContain("application.dto.auth.login.otp");assertThat(mapper).contains("adapter.in.web.auth.login.mapper","VerifyLoginOtpResult");}
 @Test void genericOtpTechnicalCoreRemainsAuthoritative() throws Exception {String port=Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice/application/port/out/OtpChallengePort.java"));String scenario=Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice/application/dto/auth/otp/OtpScenario.java"));assertThat(port).contains("AuthFlowOtpPort");assertThat(scenario).contains("REGISTRATION","LOGIN");}
}
