package s4y.guess.guesser.infra.jev;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * Settings for the Jev gateway: the API key, the base URL and the time limit for one call. The
 * key is secret, so {@link #toString()} does not show it.
 */
public record JevConfig(String apiKey, URI baseUrl, Duration timeout) {

  public static final URI DEFAULT_BASE_URL = URI.create("https://api.typesafe.ai");
  public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

  public JevConfig {
    Objects.requireNonNull(apiKey, "apiKey");
    Objects.requireNonNull(baseUrl, "baseUrl");
    Objects.requireNonNull(timeout, "timeout");
  }

  /** Settings with the default base URL and the default time limit of 10 seconds. */
  public static JevConfig withDefaults(String apiKey) {
    return new JevConfig(apiKey, DEFAULT_BASE_URL, DEFAULT_TIMEOUT);
  }

  @Override
  public String toString() {
    return "JevConfig[apiKey=***, baseUrl=" + baseUrl + ", timeout=" + timeout + "]";
  }
}
