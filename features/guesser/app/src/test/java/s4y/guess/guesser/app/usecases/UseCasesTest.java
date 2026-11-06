package s4y.guess.guesser.app.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import s4y.guess.core.domain.ports.TransactionRunner;
import s4y.guess.core.infra.memory.TransactionRunnerInMemory;
import s4y.guess.guesser.domain.errors.GameNotFound;
import s4y.guess.guesser.domain.errors.GuessFailed;
import s4y.guess.guesser.domain.vo.Bit;
import s4y.guess.guesser.domain.vo.GameId;
import s4y.guess.guesser.domain.vo.Guess;
import s4y.guess.guesser.domain.vo.GuessOutcome;
import s4y.guess.guesser.domain.vo.Round;
import s4y.guess.guesser.domain.vo.Score;
import s4y.guess.guesser.domain.workflows.GameSnapshot;
import s4y.guess.guesser.domain.workflows.PressOutcome;
import s4y.guess.guesser.infra.memory.GameRepositoryInMemory;
import s4y.guess.guesser.infra.memory.GuessGatewayScripted;

/** Runs every use case end to end on the in-memory adapters. */
class UseCasesTest {

  private final GameRepositoryInMemory games = new GameRepositoryInMemory();
  private final GuessGatewayScripted gateway = new GuessGatewayScripted();
  private final TransactionRunner tx = new TransactionRunnerInMemory();

  private final StartGameUseCase start = new StartGameUseCase(games, tx);
  private final GetGameUseCase get = new GetGameUseCase(games);
  private final PressBitUseCase press = new PressBitUseCase(games, gateway, tx);
  private final ChangeWindowSizeUseCase changeWindow = new ChangeWindowSizeUseCase(games, tx);
  private final ResetRoundsUseCase reset = new ResetRoundsUseCase(games, tx);

  private GameId startGame(Optional<Integer> windowSize) throws Exception {
    return start.execute(new StartGameUseCase.Command(windowSize)).game().id();
  }

  @Test
  void startWithoutWindowSizeUsesTheDefault() throws Exception {
    GameSnapshot snapshot = start.execute(new StartGameUseCase.Command(Optional.empty()));

    assertThat(snapshot.game().windowSize().value()).isEqualTo(32);
    assertThat(snapshot.game().rounds().size()).isZero();
    assertThat(snapshot.score()).isEqualTo(new Score(0, 0, 0));
    assertThat(games.find(snapshot.game().id())).contains(snapshot.game());
  }

  @Test
  void startKeepsAWindowSizeThatIsInRange() throws Exception {
    GameSnapshot snapshot = start.execute(new StartGameUseCase.Command(Optional.of(10)));

    assertThat(snapshot.game().windowSize().value()).isEqualTo(10);
  }

  @Test
  void startClampsTheWindowSize() throws Exception {
    assertThat(start.execute(new StartGameUseCase.Command(Optional.of(0))).game().windowSize().value())
        .isEqualTo(1);
    assertThat(start.execute(new StartGameUseCase.Command(Optional.of(999))).game().windowSize().value())
        .isEqualTo(256);
    assertThat(start.execute(new StartGameUseCase.Command(Optional.of(-5))).game().windowSize().value())
        .isEqualTo(1);
  }

  @Test
  void getReturnsTheGameAndItsScore() throws Exception {
    GameId id = startGame(Optional.of(3));

    GameSnapshot snapshot = get.execute(new GetGameUseCase.Command(id));

    assertThat(snapshot.game().id()).isEqualTo(id);
    assertThat(snapshot.game().windowSize().value()).isEqualTo(3);
    assertThat(snapshot.score()).isEqualTo(new Score(0, 0, 0));
  }

  @Test
  void getFailsForAnUnknownGame() {
    GameId unknown = new GameId(UUID.randomUUID());

    assertThatThrownBy(() -> get.execute(new GetGameUseCase.Command(unknown)))
        .isInstanceOf(GameNotFound.class);
  }

  @Test
  void pressStoresRoundsAndKeepsScore() throws Exception {
    GameId id = startGame(Optional.of(2));
    gateway.then(Guess.of(Bit.ONE)).then(Guess.of(Bit.ZERO));

    PressOutcome first = press.execute(new PressBitUseCase.Command(id, Bit.ONE));
    PressOutcome second = press.execute(new PressBitUseCase.Command(id, Bit.ONE));
    PressOutcome third = press.execute(new PressBitUseCase.Command(id, Bit.ONE));

    // The first press has no history, so there is no guess and the gateway is not called.
    assertThat(first.round()).isEqualTo(new Round.Unguessed(Bit.ONE));
    // The second press is guessed ONE and was ONE: a hit.
    assertThat(second.round()).isEqualTo(new Round.Guessed(Bit.ONE, Bit.ONE));
    // The third press is guessed ZERO but was ONE: a miss.
    assertThat(third.round()).isEqualTo(new Round.Guessed(Bit.ONE, Bit.ZERO));
    assertThat(third.score()).isEqualTo(new Score(1, 2, 3));
    assertThat(gateway.inputs()).hasSize(2);

    GameSnapshot loaded = get.execute(new GetGameUseCase.Command(id));
    assertThat(loaded.game().rounds().size()).isEqualTo(3);
    assertThat(loaded.score()).isEqualTo(new Score(1, 2, 3));
  }

  @Test
  void pressGivesTheGatewayOnlyTheWindowOfEarlierBits() throws Exception {
    GameId id = startGame(Optional.of(2));
    gateway.then(Guess.of(Bit.ZERO)).then(Guess.of(Bit.ZERO)).then(Guess.of(Bit.ZERO));

    for (Bit bit : new Bit[] {Bit.ONE, Bit.ZERO, Bit.ONE, Bit.ONE}) {
      press.execute(new PressBitUseCase.Command(id, bit));
    }

    assertThat(gateway.inputs()).extracting(bits -> bits.asText()).containsExactly("1", "10", "01");
  }

  @Test
  void pressReportsAGuessFailureAndStillStoresTheRound() throws Exception {
    GameId id = startGame(Optional.empty());
    press.execute(new PressBitUseCase.Command(id, Bit.ONE));
    gateway.thenFail(GuessFailed.Reason.TIMEOUT, "too slow");

    PressOutcome outcome = press.execute(new PressBitUseCase.Command(id, Bit.ZERO));

    assertThat(outcome.round()).isEqualTo(new Round.Unguessed(Bit.ZERO));
    assertThat(outcome.guessFailure())
        .contains(new GuessOutcome.Failed(GuessFailed.Reason.TIMEOUT, "too slow"));
    assertThat(get.execute(new GetGameUseCase.Command(id)).game().rounds().size()).isEqualTo(2);
  }

  @Test
  void pressFailsForAnUnknownGame() {
    GameId unknown = new GameId(UUID.randomUUID());

    assertThatThrownBy(() -> press.execute(new PressBitUseCase.Command(unknown, Bit.ONE)))
        .isInstanceOf(GameNotFound.class);
    assertThat(gateway.inputs()).isEmpty();
  }

  @Test
  void changeWindowSizeKeepsTheRoundsAndClamps() throws Exception {
    GameId id = startGame(Optional.of(4));
    press.execute(new PressBitUseCase.Command(id, Bit.ONE));

    GameSnapshot changed = changeWindow.execute(new ChangeWindowSizeUseCase.Command(id, 7));
    assertThat(changed.game().windowSize().value()).isEqualTo(7);
    assertThat(changed.game().rounds().size()).isEqualTo(1);

    assertThat(
            changeWindow.execute(new ChangeWindowSizeUseCase.Command(id, 0)).game().windowSize().value())
        .isEqualTo(1);
    assertThat(
            changeWindow.execute(new ChangeWindowSizeUseCase.Command(id, 999)).game().windowSize().value())
        .isEqualTo(256);
    assertThat(get.execute(new GetGameUseCase.Command(id)).game().windowSize().value())
        .isEqualTo(256);
  }

  @Test
  void changeWindowSizeFailsForAnUnknownGame() {
    GameId unknown = new GameId(UUID.randomUUID());

    assertThatThrownBy(() -> changeWindow.execute(new ChangeWindowSizeUseCase.Command(unknown, 5)))
        .isInstanceOf(GameNotFound.class);
  }

  @Test
  void resetRemovesRoundsAndKeepsTheWindowSize() throws Exception {
    GameId id = startGame(Optional.of(5));
    gateway.then(Guess.of(Bit.ONE));
    press.execute(new PressBitUseCase.Command(id, Bit.ONE));
    press.execute(new PressBitUseCase.Command(id, Bit.ONE));

    GameSnapshot snapshot = reset.execute(new ResetRoundsUseCase.Command(id));

    assertThat(snapshot.game().rounds().size()).isZero();
    assertThat(snapshot.game().windowSize().value()).isEqualTo(5);
    assertThat(snapshot.score()).isEqualTo(new Score(0, 0, 0));
    assertThat(get.execute(new GetGameUseCase.Command(id)).game().rounds().size()).isZero();
  }

  @Test
  void resetFailsForAnUnknownGame() {
    GameId unknown = new GameId(UUID.randomUUID());

    assertThatThrownBy(() -> reset.execute(new ResetRoundsUseCase.Command(unknown)))
        .isInstanceOf(GameNotFound.class);
  }

  @Test
  void theWholeFlowWorks() throws Exception {
    GameId id = startGame(Optional.of(3));
    gateway.then(Guess.of(Bit.ZERO)).then(Guess.of(Bit.ONE));

    press.execute(new PressBitUseCase.Command(id, Bit.ZERO));
    press.execute(new PressBitUseCase.Command(id, Bit.ZERO));
    PressOutcome last = press.execute(new PressBitUseCase.Command(id, Bit.ZERO));
    assertThat(last.score()).isEqualTo(new Score(1, 2, 3));

    assertThat(get.execute(new GetGameUseCase.Command(id)).score()).isEqualTo(new Score(1, 2, 3));
    assertThat(changeWindow.execute(new ChangeWindowSizeUseCase.Command(id, 0)).game().windowSize().value())
        .isEqualTo(1);
    assertThat(reset.execute(new ResetRoundsUseCase.Command(id)).game().rounds().size()).isZero();
    assertThat(get.execute(new GetGameUseCase.Command(id)).game().windowSize().value()).isEqualTo(1);
  }
}
