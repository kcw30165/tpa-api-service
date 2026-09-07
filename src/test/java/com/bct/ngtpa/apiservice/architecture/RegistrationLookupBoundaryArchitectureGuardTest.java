package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpSearchMemberApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpSearchMemberApimMapper;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupCommand;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationLookupPort;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

class RegistrationLookupBoundaryArchitectureGuardTest {

  @Test
  void applicationRegistrationLookupHasNoSpringAdapterApimRedisOrWebDependencies() {
    var classes =
        new ClassFileImporter()
            .importClasses(
                RegistrationLookupCommand.class,
                com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupResult
                    .class,
                com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupAccount
                    .class,
                RegistrationLookupPort.class);

    ArchRuleDefinition.noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework..",
            "org.springframework.http..",
            "org.springframework.web..",
            "jakarta.servlet..",
            "com.bct.ngtpa.apiservice.adapter..",
            "com.bct.ngtpa.apiservice.adapter.out.apim..",
            "io.lettuce..",
            "org.springframework.data.redis..",
            "org.slf4j..")
        .check(classes);
  }

  @Test
  void apimRegistrationDtoBoundaryDoesNotUseWebRedisSmsEmailAdapters() {
    var classes =
        new ClassFileImporter()
            .importPackages(
                "com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto",
                "com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper");

    ArchRuleDefinition.noClasses()
        .that()
        .resideInAnyPackage(
            "..adapter.out.apim.registration.dto..", "..adapter.out.apim.registration.mapper..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..adapter.in.web..",
            "..adapter.out.redis..",
            "..adapter.out.sms..",
            "..adapter.out.email..")
        .check(classes);
    assertThat(TrpSearchMemberApimRequest.class).isNotNull();
    assertThat(TrpSearchMemberApimMapper.class).isNotNull();
  }
}
