package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.dto.GetPersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.dto.PersonalInformationResult;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import java.util.Optional;
import reactor.core.publisher.Mono;

public interface GetPersonalInformationUseCase {
  Mono<PersonalInformationResult> execute(GetPersonalInformationCommand command);

  /**
   * Retrieves personal information and, where the implementation supports it, caches the
   * field-access config of the selected account in the authenticated session. The default does not
   * cache. Caching never fails the retrieval.
   */
  default Mono<PersonalInformationResult> executeAndCache(
      GetPersonalInformationCommand command,
      Optional<AuthenticatedSessionId> sessionId,
      String accountRef,
      String requestId) {
    return execute(command);
  }
}
