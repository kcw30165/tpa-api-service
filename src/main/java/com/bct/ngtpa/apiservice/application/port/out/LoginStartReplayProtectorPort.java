package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.auth.login.replay.LoginStartReplayPayload;
import com.bct.ngtpa.apiservice.application.dto.auth.login.replay.ProtectedLoginStartReplay;
import com.bct.ngtpa.apiservice.application.port.out.authflow.AuthFlowProtectedContextPort;

public interface LoginStartReplayProtectorPort
    extends AuthFlowProtectedContextPort<LoginStartReplayPayload, ProtectedLoginStartReplay> {}
