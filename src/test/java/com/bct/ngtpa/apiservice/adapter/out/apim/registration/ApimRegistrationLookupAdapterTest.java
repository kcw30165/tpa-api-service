// package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

// import static org.assertj.core.api.Assertions.assertThat;

// import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpSearchUnregisteredMemberApimDataItem;
// import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpSearchUnregisteredMemberApimMapper;
// import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpSearchUnregisteredMemberApimResultMapper;
// import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
// import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationIdentityType;
// import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupCommand;
// import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupStatus;
// import java.util.List;
// import org.junit.jupiter.api.Test;
// import reactor.core.publisher.Mono;
// import reactor.test.StepVerifier;

// class ApimRegistrationLookupAdapterTest {

//     @Test
//     void searchUnregisteredMemberCallsExpectedApimPath() {
//         var transport = new CapturingTransport(ApimRegistrationLookupEnvelope.success(List.of(item())));
//         var adapter = adapter(transport);

//         StepVerifier.create(adapter.searchUnregisteredMember(emailCommand()))
//                 .expectNextMatches(result -> result.status() == RegistrationLookupStatus.ELIGIBLE)
//                 .verifyComplete();

//         assertThat(transport.path).isEqualTo("/ws/NGTPA/v1/TRPSearchUnregisteredMember");
//     }

//     @Test
//     void searchUnregisteredMemberSendsExpectedRequestForEmailContact() {
//         var transport = new CapturingTransport(ApimRegistrationLookupEnvelope.success(List.of(item())));
//         adapter(transport).searchUnregisteredMember(emailCommand()).block();

//         assertThat(transport.request.searchBy()).isEqualTo("GPFS");
//         assertThat(transport.request.searchValue()).isEqualTo("1234567890");
//         assertThat(transport.request.dob()).isEqualTo("31/01/1980");
//         assertThat(transport.request.email()).isEqualTo("member@example.com");
//         assertThat(transport.request.mobileNumber()).isEmpty();
//         assertThat(transport.request.toString()).doesNotContain("identityTypeSearchScope", "DataDomain", "accountRef", "lang", "requestId");
//     }

//     @Test
//     void searchUnregisteredMemberSendsExpectedRequestForMobileContact() {
//         var transport = new CapturingTransport(ApimRegistrationLookupEnvelope.success(List.of(item())));
//         adapter(transport).searchUnregisteredMember(mobileCommand()).block();

//         assertThat(transport.request.searchBy()).isEqualTo("HKID");
//         assertThat(transport.request.searchValue()).isEqualTo("A1234567");
//         assertThat(transport.request.dob()).isEqualTo("15/01/1980");
//         assertThat(transport.request.email()).isEmpty();
//         assertThat(transport.request.mobileNumber()).isEqualTo("91234567");
//     }

//     @Test
//     void eligibleResponseMapsToEligibleLookupResult() {
//         StepVerifier.create(adapter(new CapturingTransport(ApimRegistrationLookupEnvelope.success(List.of(item())))).searchUnregisteredMember(emailCommand()))
//                 .assertNext(result -> {
//                     assertThat(result.status()).isEqualTo(RegistrationLookupStatus.ELIGIBLE);
//                     assertThat(result.accounts()).hasSize(1);
//                 })
//                 .verifyComplete();
//     }

//     @Test
//     void memberNotFoundBusinessResponseMapsToNotFoundResult() {
//         StepVerifier.create(adapter(new CapturingTransport(new ApimRegistrationLookupEnvelope("err.tpa.member.not.found", List.of()))).searchUnregisteredMember(emailCommand()))
//                 .assertNext(result -> {
//                     assertThat(result.status()).isEqualTo(RegistrationLookupStatus.NOT_FOUND_OR_NOT_ELIGIBLE);
//                     assertThat(result.upstreamErrorCode()).isEqualTo("err.tpa.member.not.found");
//                     assertThat(result.accounts()).isEmpty();
//                 })
//                 .verifyComplete();
//     }

//     @Test
//     void emptySuccessfulDataMapsToNotFoundResult() {
//         StepVerifier.create(adapter(new CapturingTransport(ApimRegistrationLookupEnvelope.success(List.of()))).searchUnregisteredMember(emailCommand()))
//                 .assertNext(result -> {
//                     assertThat(result.status()).isEqualTo(RegistrationLookupStatus.NOT_FOUND_OR_NOT_ELIGIBLE);
//                     assertThat(result.accounts()).isEmpty();
//                 })
//                 .verifyComplete();
//     }

//     @Test
//     void otherBusinessErrMessageMapsToBusinessRejectedResult() {
//         StepVerifier.create(adapter(new CapturingTransport(new ApimRegistrationLookupEnvelope("err.tpa.other.business.error", List.of()))).searchUnregisteredMember(emailCommand()))
//                 .assertNext(result -> {
//                     assertThat(result.status()).isEqualTo(RegistrationLookupStatus.UPSTREAM_BUSINESS_REJECTED);
//                     assertThat(result.upstreamErrorCode()).isEqualTo("err.tpa.other.business.error");
//                 })
//                 .verifyComplete();
//     }

//     @Test
//     void technicalApimFailurePropagatesExistingException() {
//         StepVerifier.create(adapter(new FailingTransport(new IllegalStateException("APIM technical failure"))).searchUnregisteredMember(emailCommand()))
//                 .expectErrorSatisfies(error -> assertThat(error)
//                         .isInstanceOf(IllegalStateException.class)
//                         .hasMessage("APIM technical failure"))
//                 .verify();
//     }

//     @Test
//     void adapterUsesP6_4ResultMapper() {
//         StepVerifier.create(adapter(new CapturingTransport(new ApimRegistrationLookupEnvelope("err.tpa.some.other.business.error", List.of()))).searchUnregisteredMember(emailCommand()))
//                 .expectNextMatches(result -> result.status() == RegistrationLookupStatus.UPSTREAM_BUSINESS_REJECTED)
//                 .verifyComplete();
//     }

//     @Test
//     void adapterDoesNotLogOrExposeSensitiveRequestValues() {
//         StepVerifier.create(adapter(new FailingTransport(new IllegalStateException("safe apim failure"))).searchUnregisteredMember(emailCommand()))
//                 .expectErrorSatisfies(error -> assertThat(error.getMessage())
//                         .doesNotContain("1234567890", "A1234567", "member@example.com", "91234567", "31/01/1980", "00000008802"))
//                 .verify();
//     }

//     private static ApimRegistrationLookupAdapter adapter(ApimRegistrationLookupTransport transport) {
//         return new ApimRegistrationLookupAdapter(
//                 transport,
//                 new TrpSearchUnregisteredMemberApimMapper(),
//                 new TrpSearchUnregisteredMemberApimResultMapper());
//     }

//     private static RegistrationLookupCommand emailCommand() {
//         return new RegistrationLookupCommand(
//                 RegistrationIdentityType.GPFS,
//                 "1234567890",
//                 "31/01/1980",
//                 RegistrationContactType.EMAIL,
//                 "member@example.com");
//     }

//     private static RegistrationLookupCommand mobileCommand() {
//         return new RegistrationLookupCommand(
//                 RegistrationIdentityType.HKID,
//                 "A1234567",
//                 "15/01/1980",
//                 RegistrationContactType.MOBILE,
//                 "91234567");
//     }

//     private static TrpSearchUnregisteredMemberApimDataItem item() {
//         return new TrpSearchUnregisteredMemberApimDataItem(
//                 "member@example.com",
//                 "91234567",
//                 "00000008802",
//                 "2",
//                 "OI",
//                 "JPM");
//     }

//     private static final class CapturingTransport implements ApimRegistrationLookupTransport {
//         private final ApimRegistrationLookupEnvelope response;
//         private String path;
//         private com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpSearchUnregisteredMemberApimRequest request;
//         private CapturingTransport(ApimRegistrationLookupEnvelope response) { this.response = response; }
//         @Override
//         public Mono<ApimRegistrationLookupEnvelope> postSearchUnregisteredMember(String path,
//                 com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpSearchUnregisteredMemberApimRequest request) {
//             this.path = path;
//             this.request = request;
//             return Mono.just(response);
//         }
//     }

//     private static final class FailingTransport implements ApimRegistrationLookupTransport {
//         private final RuntimeException error;
//         private FailingTransport(RuntimeException error) { this.error = error; }
//         @Override
//         public Mono<ApimRegistrationLookupEnvelope> postSearchUnregisteredMember(String path,
//                 com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpSearchUnregisteredMemberApimRequest request) {
//             return Mono.error(error);
//         }
//     }
// }
