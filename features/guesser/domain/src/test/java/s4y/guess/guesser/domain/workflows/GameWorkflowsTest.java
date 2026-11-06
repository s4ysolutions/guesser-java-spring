package s4y.guess.guesser.domain.workflows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static s4y.guess.guesser.domain.TestGames.game;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import s4y.guess.core.domain.ports.TransactionRunner;
import s4y.guess.core.infra.memory.TransactionRunnerInMemory;
import s4y.guess.guesser.domain.entities.Game;
import s4y.guess.guesser.domain.errors.ConcurrentPress;
import s4y.guess.guesser.domain.errors.GameNotFound;
import s4y.guess.guesser.domain.errors.GuessFailed;
import s4y.guess.guesser.domain.vo.Bit;
import s4y.guess.guesser.domain.vo.GameId;
import s4y.guess.guesser.domain.vo.Guess;
import s4y.guess.guesser.domain.vo.GuessOutcome;
import s4y.guess.guesser.domain.vo.Round;
import s4y.guess.guesser.domain.vo.Score;
import s4y.guess.guesser.domain.vo.WindowSize;

class GameWorkflowsTest {

  private final TransactionRunner tx = new TransactionRunnerInMemory();
  private final GameRepositoryFake games = new GameRepositoryFake();

  @Test
  void startCreatesAnEmptyGame() throws Exception {
    GameSnapshot snapshot = GameWorkflows.start(games, tx, new WindowSize(5));

    Game created = snapshot.game();
    assertThat(created.windowSize().value()).isEqualTo(5);
    assertThat(created.rounds().size()).isZero();
    assertThat(snapshot.score()).isEqualTo(new Score(0, 0, 0));
    assertThat(games.find(created.id())).contains(created);
  }

  @Test
  void loadReturnsGameAndScore() throws Exception {
    Game game = game(3, "01");
    games.put(game);

    GameSnapshot snapshot = GameWorkflows.load(games, game.id());

    assertThat(snapshot.game()).isEqualTo(game);
    assertThat(snapshot.score()).isEqualTo(new Score(0, 0, 2));
  }

  @Test
  void loadFailsWithGameNotFound() {
    GameId unknown = new GameId(UUID.randomUUID());

    assertThatThrownBy(() -> GameWorkflows.load(games, unknown))
        .isInstanceOfSatisfying(GameNotFound.class, e -> assertThat(e.id()).isEqualTo(unknown));
  }

  @Test
  void pressMakesTheGuessFromHistoryBeforeTheBit() throws Exception {
    Game game = game(3, "01101");
    games.put(game);
    GuessGatewayFake gateway = GuessGatewayFake.answering(Guess.of(Bit.ZERO));

    PressOutcome outcome = GameWorkflows.press(games, gateway, tx, game.id(), Bit.ZERO);

    // History is "01101", window 3: the gateway sees "101", never the pressed bit.
    assertThat(gateway.inputs()).hasSize(1);
    assertThat(gateway.inputs().get(0).asText()).isEqualTo("101");
    assertThat(outcome.round()).isEqualTo(new Round.Guessed(Bit.ZERO, Bit.ZERO));
    assertThat(outcome.guessFailure()).isEmpty();
    assertThat(outcome.score()).isEqualTo(new Score(1, 1, 6));
    assertThat(outcome.game().rounds().size()).isEqualTo(6);
    assertThat(games.saved()).containsExactly(outcome.game());
  }

  @Test
  void pressRecordsAMissWhenTheGuessIsWrong() throws Exception {
    Game game = game(3, "0");
    games.put(game);
    GuessGatewayFake gateway = GuessGatewayFake.answering(Guess.of(Bit.ZERO));

    PressOutcome outcome = GameWorkflows.press(games, gateway, tx, game.id(), Bit.ONE);

    assertThat(outcome.round()).isEqualTo(new Round.Guessed(Bit.ONE, Bit.ZERO));
    assertThat(outcome.score()).isEqualTo(new Score(0, 1, 2));
  }

  @Test
  void pressSkipsTheGuessOnEmptyHistory() throws Exception {
    Game game = game(3, "");
    games.put(game);
    GuessGatewayFake gateway = GuessGatewayFake.answering(Guess.of(Bit.ONE));

    PressOutcome outcome = GameWorkflows.press(games, gateway, tx, game.id(), Bit.ONE);

    assertThat(gateway.inputs()).isEmpty();
    assertThat(outcome.round()).isEqualTo(new Round.Unguessed(Bit.ONE));
    assertThat(outcome.guessFailure()).isEmpty();
    assertThat(outcome.score()).isEqualTo(new Score(0, 0, 1));
    assertThat(games.saved()).hasSize(1);
  }

  @Test
  void pressStoresAnUnguessedRoundWhenTheGuessFails() throws Exception {
    Game game = game(3, "01");
    games.put(game);
    GuessGatewayFake gateway =
        GuessGatewayFake.failing(new GuessFailed(GuessFailed.Reason.TIMEOUT, "too slow"));

    PressOutcome outcome = GameWorkflows.press(games, gateway, tx, game.id(), Bit.ONE);

    assertThat(outcome.round()).isEqualTo(new Round.Unguessed(Bit.ONE));
    assertThat(outcome.guessFailure())
        .contains(new GuessOutcome.Failed(GuessFailed.Reason.TIMEOUT, "too slow"));
    assertThat(outcome.score()).isEqualTo(new Score(0, 0, 3));
    assertThat(games.saved()).hasSize(1);
    assertThat(games.saved().get(0).rounds().last()).contains(new Round.Unguessed(Bit.ONE));
  }

  @Test
  void pressFailsWithConcurrentPressWhenTheRoundCountChanged() {
    Game game = game(3, "01");
    games.put(game);
    games.secondFindReturns(gameWithId(game.id(), 3, "011"));
    GuessGatewayFake gateway = GuessGatewayFake.answering(Guess.of(Bit.ONE));

    assertThatThrownBy(() -> GameWorkflows.press(games, gateway, tx, game.id(), Bit.ONE))
        .isInstanceOfSatisfying(ConcurrentPress.class, e -> assertThat(e.id()).isEqualTo(game.id()));

    assertThat(games.saved()).isEmpty();
  }

  @Test
  void pressPassesOnConcurrentPressFromTheRepository() {
    Game game = game(3, "01");
    games.put(game);
    games.conflictOnSave();
    GuessGatewayFake gateway = GuessGatewayFake.answering(Guess.of(Bit.ONE));

    assertThatThrownBy(() -> GameWorkflows.press(games, gateway, tx, game.id(), Bit.ONE))
        .isInstanceOf(ConcurrentPress.class);
  }

  @Test
  void pressFailsWithGameNotFound() {
    GameId unknown = new GameId(UUID.randomUUID());
    GuessGatewayFake gateway = GuessGatewayFake.answering(Guess.of(Bit.ONE));

    assertThatThrownBy(() -> GameWorkflows.press(games, gateway, tx, unknown, Bit.ONE))
        .isInstanceOf(GameNotFound.class);

    assertThat(gateway.inputs()).isEmpty();
    assertThat(games.saved()).isEmpty();
  }

  @Test
  void changeWindowSizeKeepsTheRounds() throws Exception {
    Game game = game(3, "0110");
    games.put(game);

    GameSnapshot snapshot =
        GameWorkflows.changeWindowSize(games, tx, game.id(), new WindowSize(7));

    assertThat(snapshot.game().windowSize().value()).isEqualTo(7);
    assertThat(snapshot.game().rounds()).isEqualTo(game.rounds());
    assertThat(games.saved()).containsExactly(snapshot.game());
  }

  @Test
  void changeWindowSizeFailsWithGameNotFound() {
    GameId unknown = new GameId(UUID.randomUUID());

    assertThatThrownBy(
            () -> GameWorkflows.changeWindowSize(games, tx, unknown, new WindowSize(2)))
        .isInstanceOf(GameNotFound.class);
  }

  @Test
  void resetRemovesRounds() throws Exception {
    Game game = game(3, "0110");
    games.put(game);

    GameSnapshot snapshot = GameWorkflows.reset(games, tx, game.id());

    assertThat(snapshot.game().rounds().size()).isZero();
    assertThat(snapshot.score()).isEqualTo(new Score(0, 0, 0));
    assertThat(games.saved()).containsExactly(snapshot.game());
  }

  @Test
  void resetFailsWithGameNotFound() {
    GameId unknown = new GameId(UUID.randomUUID());

    assertThatThrownBy(() -> GameWorkflows.reset(games, tx, unknown))
        .isInstanceOf(GameNotFound.class);
  }

  private static Game gameWithId(GameId id, int windowSize, String pressed) {
    Game other = game(windowSize, pressed);
    return new Game(id, other.windowSize(), other.rounds());
  }
}
