package s4y.guess.guesser.infra.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import s4y.guess.guesser.domain.entities.Game;
import s4y.guess.guesser.domain.vo.Bit;
import s4y.guess.guesser.domain.vo.GameId;
import s4y.guess.guesser.domain.vo.Round;
import s4y.guess.guesser.domain.vo.WindowSize;

class GameRepositoryInMemoryTest {

  private final GameRepositoryInMemory games = new GameRepositoryInMemory();

  @Test
  void createMakesAnEmptyGameWithANewId() {
    Game first = games.create(new WindowSize(4));
    Game second = games.create(new WindowSize(4));

    assertThat(first.windowSize().value()).isEqualTo(4);
    assertThat(first.rounds().size()).isZero();
    assertThat(first.id()).isNotEqualTo(second.id());
  }

  @Test
  void findReturnsWhatWasCreatedAndSaved() {
    Game created = games.create(WindowSize.defaultSize());
    assertThat(games.find(created.id())).contains(created);

    Game changed =
        new Game(created.id(), created.windowSize(), created.rounds().append(new Round.Unguessed(Bit.ONE)));
    games.save(changed);

    assertThat(games.find(created.id())).contains(changed);
  }

  @Test
  void findReturnsEmptyForAnUnknownId() {
    assertThat(games.find(new GameId(UUID.randomUUID()))).isEmpty();
  }
}
