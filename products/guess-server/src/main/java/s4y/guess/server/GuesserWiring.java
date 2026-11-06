package s4y.guess.server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import s4y.guess.core.domain.ports.TransactionRunner;
import s4y.guess.core.infra.springtx.TransactionRunnerSpring;
import s4y.guess.guesser.app.usecases.ChangeWindowSizeUseCase;
import s4y.guess.guesser.app.usecases.GetGameUseCase;
import s4y.guess.guesser.app.usecases.PressBitUseCase;
import s4y.guess.guesser.app.usecases.ResetRoundsUseCase;
import s4y.guess.guesser.app.usecases.StartGameUseCase;
import s4y.guess.guesser.domain.ports.GameRepository;
import s4y.guess.guesser.domain.ports.GuessGateway;
import s4y.guess.guesser.infra.h2.GuesserH2Config;
import s4y.guess.guesser.infra.jev.GuessGatewayJev;
import s4y.guess.guesser.infra.jev.JevConfig;
import s4y.guess.guesser.infra.longestmatch.GuessGatewayLongestMatch;
import s4y.guess.guesser.presentation.rest.GuesserRestConfig;

/**
 * The composition root of the server. This is the only class that names concrete adapters
 * (Spring transactions, longest-match, Jev). The use cases are plain classes, so they are
 * created here with {@code new}.
 *
 * <p>The property {@code guess.gateway} picks the {@link GuessGateway}: {@code longest-match}
 * (default) or {@code jev}.
 */
@Configuration
@Import({GuesserH2Config.class, GuesserRestConfig.class})
public class GuesserWiring {

  @Bean
  public TransactionRunner transactionRunner(PlatformTransactionManager transactionManager) {
    return new TransactionRunnerSpring(transactionManager);
  }

  @Bean
  @ConditionalOnProperty(
      name = "guess.gateway",
      havingValue = "longest-match",
      matchIfMissing = true)
  public GuessGateway guessGatewayLongestMatch() {
    return new GuessGatewayLongestMatch();
  }

  @Bean
  @ConditionalOnProperty(name = "guess.gateway", havingValue = "jev")
  public GuessGateway guessGatewayJev(@Value("${JEV_API_KEY:}") String apiKey) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException(
          "guess.gateway=jev needs the environment variable JEV_API_KEY, but it is empty or missing");
    }
    return new GuessGatewayJev(JevConfig.withDefaults(apiKey));
  }

  @Bean
  public StartGameUseCase startGameUseCase(GameRepository games, TransactionRunner tx) {
    return new StartGameUseCase(games, tx);
  }

  @Bean
  public GetGameUseCase getGameUseCase(GameRepository games) {
    return new GetGameUseCase(games);
  }

  @Bean
  public PressBitUseCase pressBitUseCase(
      GameRepository games, GuessGateway gateway, TransactionRunner tx) {
    return new PressBitUseCase(games, gateway, tx);
  }

  @Bean
  public ChangeWindowSizeUseCase changeWindowSizeUseCase(
      GameRepository games, TransactionRunner tx) {
    return new ChangeWindowSizeUseCase(games, tx);
  }

  @Bean
  public ResetRoundsUseCase resetRoundsUseCase(GameRepository games, TransactionRunner tx) {
    return new ResetRoundsUseCase(games, tx);
  }
}
