package com.bct.ngtpa.apiservice.adapter.out.email;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Plain SMTP client for unauthenticated internal SMTP relays.
 *
 * <p>
 * This implementation is intentionally small and dependency-free because the
 * current BCT relay is a plain port-25 relay. It avoids logging message
 * content,
 * recipient, or credentials. Sensitive operational logging remains in
 * {@link SmtpEmailSenderAdapter}.
 * </p>
 *
 * <p>
 * Supported flow: EHLO/HELO, MAIL FROM, RCPT TO, DATA, QUIT. TLS and SMTP AUTH
 * are intentionally not implemented here. If a future relay requires TLS/AUTH,
 * replace this adapter with a managed mail implementation and add explicit mail
 * dependencies at that time.
 * </p>
 */
public class PlainSocketSmtpClient implements SmtpClient {

    private static final int DEFAULT_TIMEOUT_MILLIS = 5000;

    @Override
    public Mono<Void> send(SmtpSendCommand command) {
        return Mono.fromRunnable(() -> sendBlocking(command))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private void sendBlocking(SmtpSendCommand command) {
        validate(command);
        int timeoutMillis = command.timeoutMillis() > 0
                ? command.timeoutMillis()
                : DEFAULT_TIMEOUT_MILLIS;

        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(command.host(), command.port()), timeoutMillis);
            socket.setSoTimeout(timeoutMillis);

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    BufferedWriter writer = new BufferedWriter(
                            new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
                expect(reader, 220);

                String localHost = localHostName();
                writeLine(writer, "EHLO " + localHost);
                int ehloCode = readResponseCode(reader);
                if (ehloCode != 250) {
                    writeLine(writer, "HELO " + localHost);
                    expect(reader, 250);
                }

                writeLine(writer, "MAIL FROM:<" + command.from().trim() + ">");
                expect(reader, 250);

                writeLine(writer, "RCPT TO:<" + command.to().trim() + ">");
                expect(reader, 250, 251);

                writeLine(writer, "DATA");
                expect(reader, 354);

                writeData(writer, command);
                expect(reader, 250);

                writeLine(writer, "QUIT");
                readResponseCode(reader);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("SMTP send failed", exception);
        }
    }

    private void writeData(BufferedWriter writer, SmtpSendCommand command) throws IOException {
        writeLine(writer, "From: " + command.from().trim());
        writeLine(writer, "To: " + command.to().trim());
        writeLine(writer, "Subject: " + encodedHeader(nullToEmpty(command.subject())));
        writeLine(writer, "MIME-Version: 1.0");
        writeLine(writer, "Content-Type: text/plain; charset=UTF-8");
        writeLine(writer, "Content-Transfer-Encoding: 8bit");
        writeLine(writer, "");

        String body = nullToEmpty(command.body()).replace("\r\n", "\n").replace('\r', '\n');
        for (String line : body.split("\n", -1)) {
            // SMTP dot-stuffing. A line beginning with '.' must be escaped.
            writeLine(writer, line.startsWith(".") ? "." + line : line);
        }
        writeLine(writer, ".");
    }

    private String encodedHeader(String value) {
        if (value.chars().allMatch(ch -> ch >= 32 && ch <= 126)) {
            return value;
        }
        return "=?UTF-8?B?" + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)) + "?=";
    }

    private void writeLine(BufferedWriter writer, String line) throws IOException {
        writer.write(line);
        writer.write("\r\n");
        writer.flush();
    }

    private void expect(BufferedReader reader, int... expectedCodes) throws IOException {
        int code = readResponseCode(reader);
        for (int expected : expectedCodes) {
            if (code == expected) {
                return;
            }
        }
        throw new IOException("Unexpected SMTP response code " + code);
    }

    private int readResponseCode(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null || line.length() < 3) {
            throw new IOException("SMTP server closed connection or returned malformed response");
        }
        int code = parseCode(line);
        // Multi-line SMTP response: 250-... until 250 space...
        while (line.length() > 3 && line.charAt(3) == '-') {
            line = reader.readLine();
            if (line == null || line.length() < 3) {
                throw new IOException("SMTP server closed connection during multi-line response");
            }
            code = parseCode(line);
        }
        return code;
    }

    private int parseCode(String line) throws IOException {
        try {
            return Integer.parseInt(line.substring(0, 3));
        } catch (RuntimeException exception) {
            throw new IOException("Malformed SMTP response", exception);
        }
    }

    private String localHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (RuntimeException | IOException ignored) {
            return "localhost";
        }
    }

    private void validate(SmtpSendCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        requireText(command.host(), "SMTP host must not be blank");
        if (command.port() <= 0) {
            throw new IllegalArgumentException("SMTP port must be positive");
        }
        requireText(command.from(), "SMTP from must not be blank");
        requireText(command.to(), "SMTP recipient must not be blank");
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
