package com.bct.ngtpa.apiservice.adapter.in.web.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PersonalInformationContractDocumentationGuardTest {

    @Test
    void documentationCapturesUpdateResultArrayAndOptionSetsContracts() throws Exception {
        Path document = Path.of("docs/personal-information-result-array-and-optionsets-contract.md");
        String content = Files.readString(document);

        assertThat(content).contains("PUT `/api/v1/personal-information`");
        assertThat(content).contains("`result` is the account update result array itself");
        assertThat(content).contains("must not use a nested `accounts` property");
        assertThat(content).contains("Selected account succeeded, another account failed");
        assertThat(content).contains("Selected account failed, another account succeeded");
        assertThat(content).contains("GET `/api/v1/personal-information`");
        assertThat(content).contains("optionSets.smsLanguages");
        assertThat(content).contains("ConfigVariantCandidateGenerator");
        assertThat(content).contains("code.accountEnv.trustCode.schemeType");
    }
}
