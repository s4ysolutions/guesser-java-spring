package s4y.guess.guesser.infra.h2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import s4y.guess.core.domain.ports.TransactionRunner;
import s4y.guess.core.infra.springtx.TransactionRunnerSpring;
import s4y.guess.guesser.domain.ports.GameRepository;
import s4y.guess.guesser.domain.ports.GuessGateway;
import s4y.guess.guesser.domain.vo.Bit;
import s4y.guess.guesser.domain.vo.Guess;
import s4y.guess.guesser.domain.vo.Round;
import s4y.guess.guesser.domain.vo.Score;
import s4y.guess.guesser.domain.vo.WindowSize;
import s4y.guess.guesser.domain.workflows.GameWorkflows.GameSnapshot;
import s4y.guess.guesser.domain.workflows.GameWorkflows;
import s4y.guess.guesser.domain.workflows.GameWorkflows.PressOutcome;

/** The real workflow on the real adapters: JPA repository and Spring transactions. */
@DataJpaTest(showSql = false)
@Import(GuesserH2Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GameWorkflowsOnJpaTest {

  @Autowired GameRepository games;
  @Autowired PlatformTransactionManager manager;

  /** Always guesses ONE. */
  private final GuessGateway alwaysOne = lastBits -> Guess.of(Bit.ONE);

  @Test
  void pressStoresTheRoundWithItsGuess() throws Exception {
    TransactionRunner tx = new TransactionRunnerSpring(manager);
    GameSnapshot started = GameWorkflows.start(games, tx, new WindowSize(4));
    var id = started.game().id();

    PressOutcome first = GameWorkflows.press(games, alwaysOne, tx, id, Bit.ZERO);
    PressOutcome second = GameWorkflows.press(games, alwaysOne, tx, id, Bit.ONE);

    assertThat(first.round()).isEqualTo(new Round.Unguessed(Bit.ZERO));
    assertThat(second.round()).isEqualTo(new Round.Guessed(Bit.ONE, Bit.ONE));

    GameSnapshot loaded = GameWorkflows.load(games, id);
    assertThat(loaded.game().windowSize()).isEqualTo(new WindowSize(4));
    assertThat(loaded.game().rounds().values())
        .isEqualTo(List.of(new Round.Unguessed(Bit.ZERO), new Round.Guessed(Bit.ONE, Bit.ONE)));
    assertThat(loaded.score()).isEqualTo(new Score(1, 1, 2));
  }

  @Test
  void resetAndWindowSizeChangeAreStored() throws Exception {
    TransactionRunner tx = new TransactionRunnerSpring(manager);
    var id = GameWorkflows.start(games, tx, WindowSize.defaultSize()).game().id();
    GameWorkflows.press(games, alwaysOne, tx, id, Bit.ONE);

    GameWorkflows.changeWindowSize(games, tx, id, new WindowSize(9));
    assertThat(GameWorkflows.load(games, id).game().windowSize()).isEqualTo(new WindowSize(9));
    assertThat(GameWorkflows.load(games, id).game().rounds().size()).isEqualTo(1);

    GameWorkflows.reset(games, tx, id);
    assertThat(GameWorkflows.load(games, id).game().rounds().size()).isZero();
  }
}
