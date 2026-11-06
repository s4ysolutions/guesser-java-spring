package s4y.guess.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import tools.jackson.databind.json.JsonMapper;
import s4y.guess.guesser.contract.ChangeWindowSizeRequest;
import s4y.guess.guesser.contract.ErrorResponse;
import s4y.guess.guesser.contract.GameResponse;
import s4y.guess.guesser.contract.GuesserPaths;
import s4y.guess.guesser.contract.PressRequest;
import s4y.guess.guesser.contract.PressResponse;
import s4y.guess.guesser.contract.StartGameRequest;

/**
 * Talks to the guess server over HTTP/1.1 and JSON. One method per endpoint; it returns contract
 * records. Throws {@link ServerError} for a non-2xx answer and {@link UncheckedIOException} when
 * the server cannot be reached.
 */
public class GuessHttpClient {

  private final String baseUrl;
  private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
  private final JsonMapper json = JsonMapper.builder().build();

  public GuessHttpClient(String baseUrl) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }

  public GameResponse start(Integer windowSize) {
    return send("POST", GuesserPaths.GAMES, new StartGameRequest(windowSize), GameResponse.class);
  }

  public GameResponse get(String id) {
    return send("GET", GuesserPaths.expand(GuesserPaths.GAME, id), null, GameResponse.class);
  }

  public PressResponse press(String id, int bit) {
    return send(
        "POST", GuesserPaths.expand(GuesserPaths.PRESSES, id), new PressRequest(bit), PressResponse.class);
  }

  public GameResponse changeWindowSize(String id, int windowSize) {
    return send(
        "PUT",
        GuesserPaths.expand(GuesserPaths.WINDOW_SIZE, id),
        new ChangeWindowSizeRequest(windowSize),
        GameResponse.class);
  }

  public GameResponse reset(String id) {
    return send("DELETE", GuesserPaths.expand(GuesserPaths.ROUNDS, id), null, GameResponse.class);
  }

  private <T> T send(String method, String path, Object body, Class<T> responseType) {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).header("Accept", "application/json");
    if (body == null) {
      builder.method(method, HttpRequest.BodyPublishers.noBody());
    } else {
      builder
          .header("Content-Type", "application/json")
          .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
    }
    HttpResponse<String> response;
    try {
      response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted", e);
    }
    if (response.statusCode() / 100 != 2) {
      throw toServerError(response);
    }
    return json.readValue(response.body(), responseType);
  }

  private ServerError toServerError(HttpResponse<String> response) {
    try {
      ErrorResponse error = json.readValue(response.body(), ErrorResponse.class);
      if (error != null && error.code() != null) {
        return new ServerError(error.code(), error.message(), response.statusCode());
      }
    } catch (RuntimeException ignored) {
      // The body is not an ErrorResponse; fall through to the plain error.
    }
    return new ServerError("HTTP " + response.statusCode(), response.body(), response.statusCode());
  }
}
