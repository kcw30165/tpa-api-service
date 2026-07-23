package com.bct.ngtpa.apiservice.adapter.out.apim.login;

import com.bct.ngtpa.apiservice.adapter.out.apim.login.dto.TrpMemberLoginApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.login.dto.TrpMemberLoginApimOtpItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.login.dto.TrpMemberLoginApimRequest;
import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationResult;
import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.login.LoginCredentialDestination;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class TrpMemberLoginContractMapper {

    public TrpMemberLoginApimRequest toRequest(CredentialAuthenticationCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return new TrpMemberLoginApimRequest(command.username(), command.password());
    }

    public CredentialAuthenticationResult toAuthenticated(
            String normalizedUsername,
            TrpMemberLoginApimDataItem item) {
        Objects.requireNonNull(item, "item must not be null");
        if (!item.success()) {
            throw new IllegalStateException("TRPMemberLogin success item is marked unsuccessful");
        }
        String custType = required(item.custType(), "cust-type");
        List<TrpMemberLoginApimOtpItem> source = item.otp() == null ? List.of() : item.otp();
        List<LoginCredentialDestination> destinations = new ArrayList<>(source.size());
        Set<String> unique = new HashSet<>();
        for (TrpMemberLoginApimOtpItem candidate : source) {
            if (candidate == null) {
                throw new IllegalStateException("TRPMemberLogin OTP destination item is missing");
            }
            String channel = required(candidate.channel(), "channel").toLowerCase(Locale.ROOT);
            if (!channel.equals("email") && !channel.equals("mobile")) {
                throw new IllegalStateException("TRPMemberLogin OTP channel is unsupported");
            }
            String destination = required(candidate.destination(), "destination");
            if (!unique.add(channel + '\u0000' + destination)) {
                throw new IllegalStateException("TRPMemberLogin contains a duplicate OTP destination");
            }
            destinations.add(new LoginCredentialDestination(channel, destination));
        }
        return new CredentialAuthenticationResult(
                CredentialAuthenticationStatus.AUTHENTICATED,
                required(normalizedUsername, "normalized username"),
                custType,
                trimToNull(item.userRole()),
                item.failCount() == null ? 0 : item.failCount(),
                destinations,
                null);
    }

    public CredentialAuthenticationResult failure(CredentialAuthenticationStatus status) {
        return new CredentialAuthenticationResult(status, null, null, null, null, List.of(), null);
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("TRPMemberLogin " + field + " is blank");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
