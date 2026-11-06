package s4y.guess.guesser.infra.h2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import s4y.guess.core.domain.errors.InfraFailure;
import s4y.guess.guesser.domain.entities.Game;
import s4y.guess.guesser.domain.ports.GameRepository;
import s4y.guess.guesser.domain.vo.Bit;
import s4y.guess.guesser.domain.vo.GameId;
import s4y.guess.guesser.domain.vo.Round;
import s4y.guess.guesser.domain.vo.Rounds;
import s4y.guess.guesser.domain.vo.WindowSize;

@DataJpaTest(showSql = false)
@Import(GuesserH2Config.class)
class GameRepositoryJpaTest {

  @Autowired GameRepository games;
  @Autowired EntityManager em;

  @Test
  void createsAnEmptyGameWithTheGivenWindowSize() throws Exception {
    Game created = games.create(new WindowSize(7));

    assertThat(created.windowSize()).isEqualTo(new WindowSize(7));
    assertThat(created.rounds()).isEqualTo(Rounds.empty());
    assertThat(games.find(created.id())).contains(created);
  }

  @Test
  void roundTripsGuessedAndUnguessedRoundsAndTheWindowSize() throws Exception {
    Game created = games.create(WindowSize.defaultSize());
    Game changed =
        new Game(
            created.id(),
            new WindowSize(5),
            new Rounds(
                List.of(
                    new Round.Unguessed(Bit.ZERO),
                    new Round.Guessed(Bit.ONE, Bit.ZERO),
                    new Round.Guessed(Bit.ONE, Bit.ONE))));

    games.save(changed);
    em.flush();
    em.clear(); // the next find must read from the database, not from the session

    assertThat(games.find(created.id())).contains(changed);
  }

  @Test
  void savingFewerRoundsReplacesTheStoredRounds() throws Exception {
    Game created = games.create(WindowSize.defaultSize());
    Rounds two = new Rounds(List.of(new Round.Unguessed(Bit.ONE), new Round.Unguessed(Bit.ZERO)));
    games.save(new Game(created.id(), created.windowSize(), two));
    em.flush();
    em.clear();

    games.save(new Game(created.id(), created.windowSize(), Rounds.empty()));
    em.flush();
    em.clear();

    assertThat(games.find(created.id())).contains(created);
  }

  @Test
  void findsNothingForAnUnknownId() throws Exception {
    assertThat(games.find(new GameId(UUID.randomUUID()))).isEmpty();
  }

  @Test
  void savingAnUnknownGameIsAnInfraFailure() {
    Game unknown =
        new Game(new GameId(UUID.randomUUID()), WindowSize.defaultSize(), Rounds.empty());

    assertThatThrownBy(() -> games.save(unknown))
        .isInstanceOf(InfraFailure.class)
        .hasMessage("game not found on save");
  }
}
