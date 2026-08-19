package com.bct.ngtpa.apiservice.application.service.session;

import com.bct.ngtpa.apiservice.application.dto.session.SessionId;
import com.bct.ngtpa.apiservice.application.dto.session.SessionIdGenerationCommand;

public interface SessionIdGenerator {
  SessionId generate(SessionIdGenerationCommand command);
}
