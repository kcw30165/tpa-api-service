package com.bct.ngtpa.apiservice.application.usecase;
import static org.assertj.core.api.Assertions.assertThat;
import com.bct.ngtpa.apiservice.application.dto.*;
import com.bct.ngtpa.apiservice.application.port.out.ApimReferenceDataCountriesPort;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
class GetReferenceDataCountriesServiceTest {
 @Test void passesExplicitRoutingAndMapsEnglishWithoutReactorContext(){AtomicReference<GetReferenceDataCountriesCommand> captured=new AtomicReference<>();ApimReferenceDataCountriesPort port=c->{captured.set(c);return Mono.just(items());};var r=new GetReferenceDataCountriesService(port).execute(command("en")).block();assertThat(captured.get().accountEnv()).isEqualTo("JP");assertThat(captured.get().actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_EPR);assertThat(r.countries()).extracting(ReferenceDataOption::value).containsExactly("HK","JP");}
 @Test void mapsChineseAndEnglishFallback(){var r=new GetReferenceDataCountriesService(c->Mono.just(List.of(new ReferenceDataCountryItem("HK","Hong Kong","香港","+852"),new ReferenceDataCountryItem("US","United States","","+1")))).execute(command("zh_HK")).block();assertThat(r.countries()).extracting(ReferenceDataOption::text).containsExactly("香港","United States");}
 @Test void emptyProducesEmpty(){var r=new GetReferenceDataCountriesService(c->Mono.empty()).execute(command("en")).block();assertThat(r.countries()).isEmpty();assertThat(r.callingCodes()).isEmpty();}
 private static GetReferenceDataCountriesCommand command(String lang){return new GetReferenceDataCountriesCommand(lang,"JP",ActorUserRole.ROLE_USER_EPR);} private static List<ReferenceDataCountryItem> items(){return List.of(new ReferenceDataCountryItem("HK","Hong Kong","香港","+852"),new ReferenceDataCountryItem("JP","Japan","日本","+81"));}
}
