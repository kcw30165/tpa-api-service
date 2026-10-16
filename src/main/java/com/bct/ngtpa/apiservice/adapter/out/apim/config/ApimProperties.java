package com.bct.ngtpa.apiservice.adapter.out.apim.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "apim")
@Getter
@Setter
public class ApimProperties {
  private String baseUrl;
  private int timeoutMilliseconds = 10000;

  /**
   * Maximum APIM response body buffered in memory per call (apim.max-response-size). Spring's codec
   * default is 256 KB, which large responses such as the 36-month TRPGetFundPrice export exceed.
   */
  private DataSize maxResponseSize = DataSize.ofMegabytes(16);

  private String apiKeyHeaderName = "Ocp-Apim-Subscription-Key";
  private final Oauth oauth = new Oauth();
  private final Encryption encryption = new Encryption();
  private final CredentialProfiles credentialProfiles = new CredentialProfiles();
  private final Retry retry = new Retry();
  private final Routing routing = new Routing();

  @Getter
  @Setter
  public static class Oauth {
    private String registrationId = "apim-client";
    private String clientId = "local-dev";
    private String clientSecret = "local-dev";
    private String clientAuthenticationMethod = "client_secret_basic";
    private String tokenUri = "http://localhost/token";
    private List<String> scope = new ArrayList<>();
    private int tokenRenewalSkewSeconds = 60;
    private String renewalStrategy = "client-credentials";
  }

  @Getter
  @Setter
  public static class Encryption {
    private boolean enabled;
    private String certificatePath = "/api/wssupport/v1/encryption/certificate";
    private String apiKey;
    private String privateKeyPem;
    private String publicKeyPem;
    private List<String> requestFields = new ArrayList<>();
    private List<String> responseFields = new ArrayList<>();
    private int certificateCacheTtlSeconds = 3600;
    private int certificateRenewalSkewSeconds = 60;
  }

  @Getter
  @Setter
  public static class Retry {
    private final BusinessCall businessCall = new BusinessCall();

    @Getter
    @Setter
    public static class BusinessCall {
      /** Total business-call attempts, including the initial attempt. */
      private int maxAttempts = 3;

      private long backoffMilliseconds = 100;
    }
  }

  public void validateRetryPolicy() {
    if (retry.businessCall.maxAttempts <= 0) {
      throw new IllegalStateException(
          "apim.retry.business-call.max-attempts must be greater than zero");
    }
    if (retry.businessCall.backoffMilliseconds < 0) {
      throw new IllegalStateException(
          "apim.retry.business-call-backoff-milliseconds must not be negative");
    }
  }

  /** Validated apim.max-response-size in bytes: greater than zero and at most Integer.MAX_VALUE. */
  public int maxResponseSizeBytes() {
    if (maxResponseSize == null || maxResponseSize.toBytes() <= 0) {
      throw new IllegalStateException("apim.max-response-size must be greater than zero");
    }
    if (maxResponseSize.toBytes() > Integer.MAX_VALUE) {
      throw new IllegalStateException(
          "apim.max-response-size must not exceed " + Integer.MAX_VALUE + " bytes");
    }
    return (int) maxResponseSize.toBytes();
  }

  /** Validated apim.timeout-milliseconds: must be greater than zero. */
  public int validatedTimeoutMilliseconds() {
    if (timeoutMilliseconds <= 0) {
      throw new IllegalStateException("apim.timeout-milliseconds must be greater than zero");
    }
    return timeoutMilliseconds;
  }

  @Getter
  @Setter
  public static class CredentialProfiles {
    private String defaultProfileId = "default";
    private List<Profile> profiles = new ArrayList<>();

    @Getter
    @Setter
    public static class Profile {
      private String profileId = "default";
      private String baseUrl;
      private String tokenUri;
      private String clientId;
      private String clientSecret;
      private String apiKey;
      private List<String> scope = new ArrayList<>();
      private String certificatePath = "/api/wssupport/v1/encryption/certificate";
    }
  }

  @Getter
  @Setter
  public static class Routing {
    private String nonAccountProfileId;
    private Map<String, String> accountEnvProfileMappings = new HashMap<>();
  }

  /** Validate routing configuration at startup. Throws IllegalStateException on invalid config. */
  public void validateRouting() {
    Routing r = getRouting();
    if (r == null) {
      throw new IllegalStateException("apim.routing must be configured");
    }
    if (r.getNonAccountProfileId() == null || r.getNonAccountProfileId().isBlank()) {
      throw new IllegalStateException(
          "apim.routing.non-account-profile-id must be set and non-blank");
    }
    Map<String, String> normalized = normalizeMappings(r);
    List<String> profileIds = validateProfiles(getCredentialProfiles().getProfiles());
    validateProfileReferences(r, profileIds, normalized);
  }

  private static Map<String, String> normalizeMappings(Routing r) {
    Map<String, String> normalized = new HashMap<>();
    for (Map.Entry<String, String> e : r.getAccountEnvProfileMappings().entrySet()) {
      String key = e.getKey();
      if (key == null || key.isBlank()) {
        throw new IllegalStateException(
            "apim.routing.account-env-profile-mappings contains blank key");
      }
      String norm = key.trim().toUpperCase(Locale.ROOT);
      if (normalized.containsKey(norm)) {
        throw new IllegalStateException(
            "apim.routing.account-env-profile-mappings contains duplicate key after normalization: "
                + norm);
      }
      String profileId = e.getValue();
      if (profileId == null || profileId.isBlank()) {
        throw new IllegalStateException(
            "apim.routing.account-env-profile-mappings maps to blank profile id for key " + key);
      }
      normalized.put(norm, profileId);
    }
    return normalized;
  }

  private static List<String> validateProfiles(List<CredentialProfiles.Profile> profiles) {
    List<String> profileIds = new ArrayList<>();
    for (CredentialProfiles.Profile p : profiles) {
      profileIds.add(p.getProfileId());
      validateProfile(p);
    }
    return profileIds;
  }

  private static void validateProfile(CredentialProfiles.Profile p) {
    if (p.getProfileId() == null || p.getProfileId().isBlank()) {
      throw new IllegalStateException("apim.credential-profiles.profiles contains blank profileId");
    }
    if (p.getBaseUrl() == null || p.getBaseUrl().isBlank()) {
      throw new IllegalStateException(
          "apim.credential-profiles.profile.baseUrl must be set for profile " + p.getProfileId());
    }
    if (p.getTokenUri() == null || p.getTokenUri().isBlank()) {
      throw new IllegalStateException(
          "apim.credential-profiles.profile.tokenUri must be set for profile " + p.getProfileId());
    }
    if (p.getScope() == null || p.getScope().isEmpty()) {
      throw new IllegalStateException(
          "apim.credential-profiles.profile.scope must be set for profile " + p.getProfileId());
    }
    if (p.getCertificatePath() == null || p.getCertificatePath().isBlank()) {
      throw new IllegalStateException(
          "apim.credential-profiles.profile.certificatePath must be set for profile "
              + p.getProfileId());
    }
  }

  private static void validateProfileReferences(
      Routing r, List<String> profileIds, Map<String, String> normalized) {
    if (!profileIds.contains(r.getNonAccountProfileId())) {
      throw new IllegalStateException(
          "apim.routing.non-account-profile-id references unknown profile id");
    }
    for (String target : normalized.values()) {
      if (!profileIds.contains(target)) {
        throw new IllegalStateException(
            "apim.routing.account-env-profile-mappings references unknown profile id: " + target);
      }
    }
  }
}
