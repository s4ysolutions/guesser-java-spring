package s4y.guess.guesser.infra.jev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import s4y.guess.guesser.domain.errors.GuessFailed;
import s4y.guess.guesser.domain.errors.GuessFailed.Reason;
import s4y.guess.guesser.domain.vo.Bit;
import s4y.guess.guesser.domain.vo.Bits;
import s4y.guess.guesser.domain.vo.Guess;

class GuessGatewayJevTest {

  /** What the stub server saw for one request. */
  private record Seen(String method, String path, String authorization, String body) {}

  private final List<Seen> seen = new CopyOnWriteArrayList<>();
  private volatile int status = 200;
  private volatile String responseBody = "";
  private volatile long delayMillis = 0;
  private HttpServer server;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/",
        exchange -> {
          String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          seen.add(
              new Seen(
                  exchange.getRequestMethod(),
                  exchange.getRequestURI().getPath(),
                  exchange.getRequestHeaders().getFirst("Authorization"),
                  body));
          try {
            if (delayMillis > 0) {
              Thread.sleep(delayMillis);
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
          if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
          }
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  private GuessGatewayJev gateway(Duration timeout) {
    URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    return new GuessGatewayJev(new JevConfig("test-key", base, timeout));
  }

  private GuessGatewayJev gateway() {
    return gateway(Duration.ofSeconds(5));
  }

  private static Bits bits(Bit... values) {
    return new Bits(List.of(values));
  }

  private static String answer(String answerJson) {
    return "{\"model\":\"jev-1\",\"answers\":{\"next\":" + answerJson + "}}";
  }

  private GuessFailed failureOf(Bits history) {
    return org.junit.jupiter.api.Assertions.assertThrows(
        GuessFailed.class, () -> gateway(Duration.ofMillis(200)).guess(history));
  }

  @Test
  void mapsChoiceAndConfidence() throws Exception {
    responseBody = answer("{\"type\":\"choice\",\"choice\":\"1\",\"confidence\":0.8}");

    Guess guess = gateway().guess(bits(Bit.ZERO));

    assertThat(guess.bit()).isEqualTo(Bit.ONE);
    assertThat(guess.confidence()).hasValue(0.8);
  }

  @Test
  void choiceWithoutConfidenceGivesNoConfidence() throws Exception {
    responseBody = answer("{\"type\":\"choice\",\"choice\":\"1\"}");

    Guess guess = gateway().guess(bits(Bit.ZERO));

    assertThat(guess).isEqualTo(Guess.of(Bit.ONE));
    assertThat(guess.confidence()).isEmpty();
  }

  @Test
  void ignoresFieldsItDoesNotKnow() throws Exception {
    responseBody =
        "{\"model\":\"jev-1.13.0\",\"answers\":{\"next\":{\"type\":\"choice\",\"choice\":\"0\","
            + "\"confidence\":0.9,\"probabilities\":{\"0\":0.9,\"1\":0.1}}},\"usage\":{\"tokens\":3}}";

    assertThat(gateway().guess(bits(Bit.ONE)).bit()).isEqualTo(Bit.ZERO);
  }

  @Test
  void sendsOnePostWithBearerKeyAndTheBitsAsState() throws Exception {
    responseBody = answer("{\"type\":\"choice\",\"choice\":\"0\"}");

    gateway().guess(bits(Bit.ZERO, Bit.ONE, Bit.ONE, Bit.ZERO));

    assertThat(seen).hasSize(1);
    Seen request = seen.get(0);
    assertThat(request.method()).isEqualTo("POST");
    assertThat(request.path()).isEqualTo("/v1/systemone");
    assertThat(request.authorization()).isEqualTo("Bearer test-key");

    JsonNode body = JsonMapper.builder().build().readTree(request.body());
    assertThat(body.get("model").asString()).isEqualTo("jev-latest");
    assertThat(body.get("state").asString()).isEqualTo("0110");
    JsonNode question = body.get("questions").get("next");
    assertThat(body.get("questions").size()).isEqualTo(1);
    assertThat(question.get("type").asString()).isEqualTo("choice");
    assertThat(question.get("criteria").propertyNames()).containsExactly("0", "1");
  }

  @Test
  void aBaseUrlWithATrailingSlashWorks() throws Exception {
    responseBody = answer("{\"type\":\"choice\",\"choice\":\"1\"}");
    URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");

    new GuessGatewayJev(new JevConfig("test-key", base, Duration.ofSeconds(5)))
        .guess(bits(Bit.ONE));

    assertThat(seen.get(0).path()).isEqualTo("/v1/systemone");
  }

  @Test
  void httpErrorsAreUnavailable() {
    for (int code : new int[] {500, 401, 429}) {
      seen.clear();
      status = code;
      responseBody = "no";

      GuessFailed failure = failureOf(bits(Bit.ONE));

      assertThat(failure.reason()).isEqualTo(Reason.UNAVAILABLE);
      assertThat(failure.getMessage()).isEqualTo("HTTP " + code);
    }
  }

  @Test
  void aServerThatIsDownIsUnavailable() {
    int port = server.getAddress().getPort();
    server.stop(0);
    GuessGatewayJev gateway =
        new GuessGatewayJev(
            new JevConfig("test-key", URI.create("http://127.0.0.1:" + port), Duration.ofSeconds(2)));

    assertThatThrownBy(() -> gateway.guess(bits(Bit.ONE)))
        .isInstanceOfSatisfying(
            GuessFailed.class, e -> assertThat(e.reason()).isEqualTo(Reason.UNAVAILABLE));
  }

  @Test
  void badJsonIsBadAnswer() {
    responseBody = "<html>";

    assertThat(failureOf(bits(Bit.ONE)).reason()).isEqualTo(Reason.BAD_ANSWER);
  }

  @Test
  void missingOrUnusableAnswersAreBadAnswer() {
    List<String> bodies =
        List.of(
            "{\"model\":\"jev-1\",\"answers\":{}}",
            "{\"nothing\":true}",
            answer("{\"type\":\"choice\"}"),
            answer("{\"type\":\"choice\",\"choice\":\"2\"}"),
            answer("null"));
    for (String body : bodies) {
      responseBody = body;

      assertThat(failureOf(bits(Bit.ONE)).reason()).as(body).isEqualTo(Reason.BAD_ANSWER);
    }
  }

  @Test
  void aSlowServerIsATimeout() {
    responseBody = answer("{\"type\":\"choice\",\"choice\":\"1\"}");
    delayMillis = 1500;

    assertThat(failureOf(bits(Bit.ONE)).reason()).isEqualTo(Reason.TIMEOUT);
  }

  @Test
  void theConfigDoesNotShowTheKeyInText() {
    assertThat(JevConfig.withDefaults("test-key").toString()).doesNotContain("test-key");
  }
}
