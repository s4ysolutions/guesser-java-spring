package s4y.guess.guesser.infra.jev;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalDouble;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import s4y.guess.guesser.domain.errors.GuessFailed;
import s4y.guess.guesser.domain.errors.GuessFailed.Reason;
import s4y.guess.guesser.domain.ports.GuessGateway;
import s4y.guess.guesser.domain.vo.Bit;
import s4y.guess.guesser.domain.vo.Bits;
import s4y.guess.guesser.domain.vo.Guess;

/**
 * {@link GuessGateway} that asks the TypeSafe Jev API for the next bit. It sends one choice
 * question ("0" or "1") and reads the choice and the confidence from the answer.
 *
 * <p>Errors: a network error or an HTTP status that is not 2xx gives UNAVAILABLE; a call that
 * takes longer than the configured timeout gives TIMEOUT; an answer that cannot be read or does
 * not contain "0" or "1" gives BAD_ANSWER.
 */
public final class GuessGatewayJev implements GuessGateway {

  private static final String PATH = "/v1/systemone";
  private static final String QUESTION_ID = "next";
  private static final String INSTRUCTIONS =
      "The state is a sequence of bits typed by a person. Predict the next bit the person will type.";

  private final JevConfig config;
  private final URI endpoint;
  private final HttpClient client;
  private final JsonMapper mapper =
      JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  public GuessGatewayJev(JevConfig config) {
    this.config = config;
    String base = config.baseUrl().toString();
    this.endpoint = URI.create((base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + PATH);
    this.client =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(config.timeout())
            .build();
  }

  @Override
  public Guess guess(Bits lastBits) throws GuessFailed {
    HttpRequest request =
        HttpRequest.newBuilder(endpoint)
            .timeout(config.timeout())
            .header("Authorization", "Bearer " + config.apiKey())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody(lastBits)))
            .build();

    HttpResponse<String> response;
    try {
      response = client.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (HttpTimeoutException e) {
      throw new GuessFailed(Reason.TIMEOUT, "Jev did not answer in " + config.timeout(), e);
    } catch (IOException e) {
      throw new GuessFailed(Reason.UNAVAILABLE, "Jev call failed: " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new GuessFailed(Reason.UNAVAILABLE, "Jev call was interrupted", e);
    }

    int status = response.statusCode();
    if (status < 200 || status >= 300) {
      throw new GuessFailed(Reason.UNAVAILABLE, "HTTP " + status);
    }
    return parseGuess(response.body());
  }

  private String requestBody(Bits lastBits) {
    // A LinkedHashMap keeps the options in the order 0, 1. Map.of would give a random order.
    Map<String, String> criteria = new LinkedHashMap<>();
    criteria.put("0", "The next bit is 0");
    criteria.put("1", "The next bit is 1");
    JevWire.Question question = new JevWire.Question("choice", INSTRUCTIONS, criteria);
    JevWire.Request request =
        new JevWire.Request("jev-latest", lastBits.asText(), Map.of(QUESTION_ID, question));
    return mapper.writeValueAsString(request);
  }

  private Guess parseGuess(String body) throws GuessFailed {
    JevWire.Response parsed;
    try {
      parsed = mapper.readValue(body, JevWire.Response.class);
    } catch (JacksonException e) {
      throw new GuessFailed(Reason.BAD_ANSWER, "Response is not valid JSON", e);
    }
    JevWire.Answer answer = parsed == null || parsed.answers() == null ? null : parsed.answers().get(QUESTION_ID);
    if (answer == null) {
      throw new GuessFailed(Reason.BAD_ANSWER, "No answer in response");
    }
    Bit bit =
        switch (answer.choice() == null ? "" : answer.choice()) {
          case "0" -> Bit.ZERO;
          case "1" -> Bit.ONE;
          default -> throw new GuessFailed(Reason.BAD_ANSWER, "Choice is not 0 or 1");
        };
    OptionalDouble confidence =
        answer.confidence() instanceof Number number
            ? OptionalDouble.of(number.doubleValue())
            : OptionalDouble.empty();
    return new Guess(bit, confidence);
  }
}
