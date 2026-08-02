package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "run.001a6.fail-first", matches = "true")
class AuthenticatedLoginHandoffRecoverySubtask006001a6FailFirstTest {

        private static final Path MAIN = Path.of("src/main/java/com/bct/ngtpa/apiservice");
        private static final Path VERIFY_SERVICE = MAIN.resolve(
                        "application/usecase/auth/login/otp/verify/LoginOtpVerifyService.java");

        @Test
        void committedProofCarriesExisting001a5RecoveryIdentity() throws Exception {
                String proof = Files.readString(MAIN.resolve(
                                "application/dto/auth/login/otp/verify/LoginOtpConsumptionProof.java"));
                String result = Files.readString(MAIN.resolve(
                                "application/dto/auth/login/otp/verify/LoginOtpVerifyPersistenceResult.java"));

                assertThat(proof)
                                .contains("AuthenticatedLoginHandoffReference handoffReference")
                                .contains("LoginOtpVerificationOperationReference operationReference")
                                .contains("String reservationHash")
                                .contains("Instant committedAt")
                                .contains("LoginOtpConsumptionProof[<redacted>]");
                assertThat(result).contains("LoginOtpConsumptionProof proof");
        }

        @Test
        void firstSuccessfulConsumptionForwardsTheCommittedProofIntoContinuation() throws Exception {
                String source = Files.readString(VERIFY_SERVICE);

                assertThat(source)
                                .contains("case VERIFIED_AND_CONSUMED ->")
                                .contains("continueToAccounts(c, x, r.proof())");
        }

        @Test
        void committedReplayResumesWithoutCallingOtpVerificationAgain() throws Exception {
                String source = Files.readString(VERIFY_SERVICE);
                int recovery = source.indexOf("recoverCommittedLogin(r.proof(), c, x)");
                int verification = source.indexOf("otpVerifyPersistence.verify(command)");

                assertThat(recovery).isGreaterThanOrEqualTo(0);
                assertThat(source).contains("case ALREADY_COMMITTED -> recoverCommittedLogin(r.proof(), c, x)");
                assertThat(source).doesNotContain(
                                "case ALREADY_COMMITTED -> Mono.just(result(VerifyLoginOtpStatus.SYSTEM_FAILED");
                assertThat(verification).isGreaterThanOrEqualTo(0);
        }

        @Test
        void authenticatedSessionEstablishmentReceivesRecoveryIdentityAndRemainsAtMostOnce() throws Exception {
                String verify = Files.readString(VERIFY_SERVICE);
                String establishCommand = Files.readString(MAIN.resolve(
                                "application/usecase/auth/login/session/EstablishAuthenticatedLoginSessionCommand.java"));
                String establishService = Files.readString(MAIN.resolve(
                                "application/usecase/auth/login/session/EstablishAuthenticatedLoginSessionService.java"));

                assertThat(establishCommand)
                                .contains("AuthenticatedLoginHandoffReference")
                                .contains("LoginOtpVerificationOperationReference");
                assertThat(verify)
                                .contains("r.proof().handoffReference()")
                                .contains("r.proof().operationReference()");
                assertThat(establishService)
                                .contains("loadByHandoff")
                                .contains("createIfAbsent")
                                .contains("recordCleanupProof");
        }
}
