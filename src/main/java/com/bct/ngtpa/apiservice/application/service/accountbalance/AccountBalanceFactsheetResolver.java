package com.bct.ngtpa.apiservice.application.service.accountbalance;
import com.bct.ngtpa.apiservice.application.port.out.AccountBalancePresentationEventPort;
import java.net.URI;
import java.util.Optional;
public final class AccountBalanceFactsheetResolver {
 private final AccountBalancePresentationEventPort events;
 public AccountBalanceFactsheetResolver(AccountBalancePresentationEventPort events){this.events=java.util.Objects.requireNonNull(events);}
 public Optional<String> resolve(String value){if(value==null||value.isBlank())return Optional.empty();try{URI parsed=URI.create(value);if(!parsed.isAbsolute())throw new IllegalArgumentException();return Optional.of(value);}catch(RuntimeException ex){events.malformedFactsheet();return Optional.empty();}}
}
