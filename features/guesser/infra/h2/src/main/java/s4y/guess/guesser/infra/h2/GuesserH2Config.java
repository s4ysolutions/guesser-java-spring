package s4y.guess.guesser.infra.h2;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import s4y.guess.guesser.domain.ports.GameRepository;

/**
 * Spring setup of this module: finds the row class and the Spring Data repository, and offers
 * the {@link GameRepository} port as a bean. A product imports this class.
 */
@Configuration
@EnableJpaRepositories(basePackageClasses = GameRowRepository.class)
@EntityScan(basePackageClasses = GameRow.class)
public class GuesserH2Config {

  @Bean
  public GameRepository gameRepositoryJpa(GameRowRepository rows) {
    return new GameRepositoryJpa(rows);
  }
}
