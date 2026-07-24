package com.bct.ngtpa.apiservice.application.service.auth.login.context;

import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.*;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.resend.*;
import com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.*;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.*;
import java.time.*;
import java.util.*;
import reactor.core.publisher.Mono;

public final class DefaultLoginOtpResendContextLoader implements LoginOtpResendContextLoader {
    private final AuthFlowSessionLifecycleService lifecycle;
    private final AuthFlowSessionCsrfService csrf;
    private final LoginPreAuthContextProtectorPort protector;
    private final Clock clock;

    public DefaultLoginOtpResendContextLoader(AuthFlowSessionLifecycleService l, AuthFlowSessionCsrfService c,
            LoginPreAuthContextProtectorPort p, Clock k) {
        lifecycle = Objects.requireNonNull(l);
        csrf = Objects.requireNonNull(c);
        protector = Objects.requireNonNull(p);
        clock = Objects.requireNonNull(k);
    }

    public Mono<LoginOtpResendContextResult> load(LoginOtpResendContextCommand c) {
        var id = new AuthFlowSessionId(c.authFlowSessionId());
        Instant now = clock.instant();
        return csrf
                .validateForSession(new ValidateAuthFlowSessionCsrfCommand(id, c.csrfToken(), now, c.origin(),
                        c.referer(), List.of(), c.correlationId()))
                .flatMap(v -> !v.valid() ? Mono.just(LoginOtpResendContextResult.invalid(map(v.status())))
                        : lifecycle.load(new LoadAuthFlowSessionCommand(id, c.correlationId()))
                                .map(o -> validate(o, now)))
                .onErrorReturn(LoginOtpResendContextResult.invalid(LoginOtpResendContextStatus.CSRF_INVALID));
    }

    private LoginOtpResendContextResult validate(Optional<AuthFlowSession> o, Instant now) {
        if (o.isEmpty())
            return invalid(LoginOtpResendContextStatus.MISSING_SESSION);
        var s = o.get();
        if (s.isExpired(now) || s.isTerminal())
            return invalid(LoginOtpResendContextStatus.EXPIRED_OR_TERMINAL);
        if (s.scenario() != AuthFlowScenario.LOGIN)
            return invalid(LoginOtpResendContextStatus.WRONG_SCENARIO);
        if (s.status() != AuthFlowStatus.OTP_SENT)
            return invalid(LoginOtpResendContextStatus.INVALID_STATUS);
        if (blank(s.activeOtpChallengeId()))
            return invalid(LoginOtpResendContextStatus.MISSING_ACTIVE_OTP);
        Map<String, String> a = s.data() == null ? Map.of() : s.data().attributes();
        String v = a.get("loginContext.keyVersion"), alg = a.get("loginContext.algorithm"),
                iv = a.get("loginContext.iv"), ct = a.get("loginContext.ciphertext");
        if (blank(v) || blank(alg) || blank(iv) || blank(ct))
            return invalid(LoginOtpResendContextStatus.MISSING_CONTEXT);
        try {
            LoginPreAuthContext context = protector.unprotect(new LoginPreAuthEncryptedContext(v, alg, iv, ct));
            LoginPreAuthDestination d = context.destinationsById().values().stream()
                    .filter(x -> Objects.equals(x.maskedDestination(), s.data().maskedDestination())).findFirst()
                    .orElse(null);
            if (d == null)
                return invalid(LoginOtpResendContextStatus.MISSING_CONTEXT);
            LoginOtpChannel channel = switch (d.channel().toLowerCase(Locale.ROOT)) {
                case "email" -> LoginOtpChannel.EMAIL;
                case "mobile", "sms" -> LoginOtpChannel.SMS;
                default -> null;
            };
            return channel == null ? invalid(LoginOtpResendContextStatus.MISSING_CONTEXT)
                    : LoginOtpResendContextResult.valid(s.activeOtpChallengeId(), channel, d.destination(),
                            d.maskedDestination());
        } catch (RuntimeException e) {
            return invalid(LoginOtpResendContextStatus.CONTEXT_DECRYPTION_FAILED);
        }
    }

    private LoginOtpResendContextStatus map(AuthFlowSessionCsrfValidationStatus s) {
        if (s == AuthFlowSessionCsrfValidationStatus.SESSION_MISSING)
            return LoginOtpResendContextStatus.MISSING_SESSION;
        if (s == AuthFlowSessionCsrfValidationStatus.SESSION_EXPIRED
                || s == AuthFlowSessionCsrfValidationStatus.SESSION_NOT_USABLE)
            return LoginOtpResendContextStatus.EXPIRED_OR_TERMINAL;
        if (s == AuthFlowSessionCsrfValidationStatus.ORIGIN_REFERER_INVALID)
            return LoginOtpResendContextStatus.ORIGIN_REFERER_INVALID;
        return LoginOtpResendContextStatus.CSRF_INVALID;
    }

    private LoginOtpResendContextResult invalid(LoginOtpResendContextStatus s) {
        return LoginOtpResendContextResult.invalid(s);
    }

    private boolean blank(String v) {
        return v == null || v.isBlank();
    }
}
