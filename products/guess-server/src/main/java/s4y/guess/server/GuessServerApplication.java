package s4y.guess.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the guess server. Only this package is scanned. The feature modules come in
 * through {@link GuesserWiring}.
 */
@SpringBootApplication(scanBasePackageClasses = GuessServerApplication.class)
public class GuessServerApplication {

  public static void main(String[] args) {
    SpringApplication.run(GuessServerApplication.class, args);
  }
}
