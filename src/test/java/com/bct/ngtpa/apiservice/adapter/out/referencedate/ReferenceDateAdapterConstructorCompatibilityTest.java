package com.bct.ngtpa.apiservice.adapter.out.referencedate;
import static org.assertj.core.api.Assertions.assertThat;
import com.bct.ngtpa.apiservice.adapter.out.configserver.ReferenceDateProperties;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
class ReferenceDateAdapterConstructorCompatibilityTest {
 @Test void readOnlyEnvironmentConstructorIsAvailable(){
  assertThat(new ReferenceDateAdapter(new ReferenceDateProperties(),Optional.<CachePort>empty(),"ngtpa",new MockEnvironment(),Clock.systemUTC())).isNotNull();
 }
}
