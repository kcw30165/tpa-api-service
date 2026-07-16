package com.bct.ngtpa.apiservice.application.dto.auth.registration;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum RegistrationIdentityType {
    GPN,
    GPFS,
    @JsonProperty("STAFF_NO")
    STAFF,
    ID
}
