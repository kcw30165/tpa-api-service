package com.bct.ngtpa.apiservice.adapter.out.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * Branch-oriented tests for {@link PlainSocketSmtpClient}. This class has ZERO constructor
 * dependencies and opens a raw {@link Socket} directly, so there is no injectable seam to mock.
 * Instead, an in-process, background-thread {@link ServerSocket} fake SMTP server is used to speak
 * the real line protocol (response codes 220/250/354/221) against the real client. This is an
 * integration-style test (real socket I/O), not a pure Mockito unit test.
 */
class PlainSocketSmtpClientTest {

  @FunctionalInterface
  private interface ServerScript {
    void run(BufferedReader in, BufferedWriter out) throws IOException;
  }

  /** Starts a background-thread fake SMTP server on an ephemeral loopback port. */
  private static int startFakeServer(ServerScript script) throws IOException {
    ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
    int port = serverSocket.getLocalPort();
    Thread thread =
        new Thread(
            () -> {
              try (ServerSocket ss = serverSocket;
                  Socket socket = ss.accept();
                  BufferedReader in =
                      new BufferedReader(
                          new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                  BufferedWriter out =
                      new BufferedWriter(
                          new OutputStreamWriter(
                              socket.getOutputStream(), StandardCharsets.UTF_8))) {
                script.run(in, out);
              } catch (IOException ignored) {
                // Protocol mismatches surface as client-side assertion failures instead.
              }
            });
    thread.setDaemon(true);
    thread.start();
    return port;
  }

  private static void writeLine(BufferedWriter writer, String line) throws IOException {
    writer.write(line);
    writer.write("\r\n");
    writer.flush();
  }

  private static String readLine(BufferedReader reader) throws IOException {
    String line = reader.readLine();
    if (line == null) {
      throw new IOException("client closed connection unexpectedly");
    }
    return line;
  }

  private static SmtpSendCommand command(int port, String subject, String body) {
    return new SmtpSendCommand(
        "127.0.0.1", port, 3000, "from@example.com", "to@example.com", subject, body);
  }

  // ----- happy path + protocol content -------------------------------------------

  @Test
  void happyPathCompletesAndTransmitsDotStuffedBody() throws Exception {
    List<String> dataLines = Collections.synchronizedList(new ArrayList<>());
    int port =
        startFakeServer(
            (in, out) -> {
              writeLine(out, "220 fake.smtp.local ESMTP");
              readLine(in); // EHLO
              writeLine(out, "250 OK");
              readLine(in); // MAIL FROM
              writeLine(out, "250 OK");
              readLine(in); // RCPT TO
              writeLine(out, "250 OK");
              readLine(in); // DATA
              writeLine(out, "354 Start mail input");
              String line;
              while ((line = in.readLine()) != null) {
                if (line.equals(".")) {
                  break;
                }
                dataLines.add(line);
              }
              writeLine(out, "250 OK");
              readLine(in); // QUIT
              writeLine(out, "221 Bye");
            });

    StepVerifier.create(
            new PlainSocketSmtpClient().send(command(port, "Hello Subject", ".Line one\nLine two")))
        .expectComplete()
        .verify(Duration.ofSeconds(5));

    assertThat(dataLines).contains("..Line one");
    assertThat(dataLines).contains("Line two");
    assertThat(dataLines).contains("Subject: Hello Subject");
  }

  @Test
  void ehloRejectedFallsBackToHelo() throws Exception {
    List<String> commandLines = Collections.synchronizedList(new ArrayList<>());
    int port =
        startFakeServer(
            (in, out) -> {
              writeLine(out, "220 fake.smtp.local ESMTP");
              commandLines.add(readLine(in)); // EHLO
              writeLine(out, "500 Command not recognized");
              commandLines.add(readLine(in)); // HELO
              writeLine(out, "250 OK");
              readLine(in); // MAIL FROM
              writeLine(out, "250 OK");
              readLine(in); // RCPT TO
              writeLine(out, "250 OK");
              readLine(in); // DATA
              writeLine(out, "354 Start mail input");
              String line;
              while ((line = in.readLine()) != null) {
                if (line.equals(".")) {
                  break;
                }
              }
              writeLine(out, "250 OK");
              readLine(in); // QUIT
              writeLine(out, "221 Bye");
            });

    StepVerifier.create(new PlainSocketSmtpClient().send(command(port, "Subject", "Body")))
        .expectComplete()
        .verify(Duration.ofSeconds(5));

    assertThat(commandLines).hasSize(2);
    assertThat(commandLines.get(0)).startsWith("EHLO");
    assertThat(commandLines.get(1)).startsWith("HELO");
  }

  @Test
  void multiLineEhloResponseIsHandled() throws Exception {
    int port =
        startFakeServer(
            (in, out) -> {
              writeLine(out, "220 fake.smtp.local ESMTP");
              readLine(in); // EHLO
              writeLine(out, "250-fake.smtp.local Hello");
              writeLine(out, "250 OK");
              readLine(in); // MAIL FROM
              writeLine(out, "250 OK");
              readLine(in); // RCPT TO
              writeLine(out, "250 OK");
              readLine(in); // DATA
              writeLine(out, "354 Start mail input");
              String line;
              while ((line = in.readLine()) != null) {
                if (line.equals(".")) {
                  break;
                }
              }
              writeLine(out, "250 OK");
              readLine(in); // QUIT
              writeLine(out, "221 Bye");
            });

    StepVerifier.create(new PlainSocketSmtpClient().send(command(port, "Subject", "Body")))
        .expectComplete()
        .verify(Duration.ofSeconds(5));
  }

  @Test
  void nonAsciiSubjectIsBase64Encoded() throws Exception {
    List<String> dataLines = Collections.synchronizedList(new ArrayList<>());
    int port =
        startFakeServer(
            (in, out) -> {
              writeLine(out, "220 fake.smtp.local ESMTP");
              readLine(in);
              writeLine(out, "250 OK");
              readLine(in);
              writeLine(out, "250 OK");
              readLine(in);
              writeLine(out, "250 OK");
              readLine(in);
              writeLine(out, "354 Start mail input");
              String line;
              while ((line = in.readLine()) != null) {
                if (line.equals(".")) {
                  break;
                }
                dataLines.add(line);
              }
              writeLine(out, "250 OK");
              readLine(in);
              writeLine(out, "221 Bye");
            });

    StepVerifier.create(new PlainSocketSmtpClient().send(command(port, "H\u00e9llo", "Body")))
        .expectComplete()
        .verify(Duration.ofSeconds(5));

    String subjectLine =
        dataLines.stream().filter(l -> l.startsWith("Subject:")).findFirst().orElseThrow();
    assertThat(subjectLine).contains("=?UTF-8?B?");
  }

  // ----- protocol failure branches -------------------------------------------

  @Test
  void unexpectedResponseAtMailFromThrows() throws Exception {
    int port =
        startFakeServer(
            (in, out) -> {
              writeLine(out, "220 fake.smtp.local ESMTP");
              readLine(in); // EHLO
              writeLine(out, "250 OK");
              readLine(in); // MAIL FROM
              writeLine(out, "550 Mailbox unavailable");
            });

    StepVerifier.create(new PlainSocketSmtpClient().send(command(port, "Subject", "Body")))
        .expectError(IllegalStateException.class)
        .verify(Duration.ofSeconds(5));
  }

  @Test
  void malformedInitialResponseThrows() throws Exception {
    int port = startFakeServer((in, out) -> writeLine(out, "12"));

    StepVerifier.create(new PlainSocketSmtpClient().send(command(port, "Subject", "Body")))
        .expectError(IllegalStateException.class)
        .verify(Duration.ofSeconds(5));
  }

  @Test
  void connectionRefusedThrowsIllegalStateException() throws IOException {
    int port;
    try (ServerSocket temp = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      port = temp.getLocalPort();
    }
    // Socket closed immediately above; nothing is listening on this port now.

    StepVerifier.create(new PlainSocketSmtpClient().send(command(port, "Subject", "Body")))
        .expectError(IllegalStateException.class)
        .verify(Duration.ofSeconds(5));
  }

  @Test
  void socketTimeoutThrowsIllegalStateException() throws Exception {
    int port =
        startFakeServer(
            (in, out) -> {
              try {
                Thread.sleep(2000);
              } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
              }
            });

    SmtpSendCommand cmd =
        new SmtpSendCommand(
            "127.0.0.1", port, 200, "from@example.com", "to@example.com", "Subject", "Body");

    StepVerifier.create(new PlainSocketSmtpClient().send(cmd))
        .expectError(IllegalStateException.class)
        .verify(Duration.ofSeconds(5));
  }

  // ----- validation guards (no socket needed - fail before connect) -------------

  @Test
  void nullCommandErrors() {
    StepVerifier.create(new PlainSocketSmtpClient().send(null))
        .expectError()
        .verify(Duration.ofSeconds(5));
  }

  @Test
  void blankHostErrors() {
    SmtpSendCommand cmd = new SmtpSendCommand("   ", 25, 1000, "a@b.com", "c@d.com", "s", "b");
    StepVerifier.create(new PlainSocketSmtpClient().send(cmd))
        .expectError(IllegalArgumentException.class)
        .verify(Duration.ofSeconds(5));
  }

  @Test
  void nonPositivePortErrors() {
    SmtpSendCommand cmd = new SmtpSendCommand("localhost", 0, 1000, "a@b.com", "c@d.com", "s", "b");
    StepVerifier.create(new PlainSocketSmtpClient().send(cmd))
        .expectError(IllegalArgumentException.class)
        .verify(Duration.ofSeconds(5));
  }

  @Test
  void blankFromErrors() {
    SmtpSendCommand cmd = new SmtpSendCommand("localhost", 25, 1000, "  ", "c@d.com", "s", "b");
    StepVerifier.create(new PlainSocketSmtpClient().send(cmd))
        .expectError(IllegalArgumentException.class)
        .verify(Duration.ofSeconds(5));
  }

  @Test
  void blankToErrors() {
    SmtpSendCommand cmd = new SmtpSendCommand("localhost", 25, 1000, "a@b.com", "  ", "s", "b");
    StepVerifier.create(new PlainSocketSmtpClient().send(cmd))
        .expectError(IllegalArgumentException.class)
        .verify(Duration.ofSeconds(5));
  }
}
