package s4y.guess.guesser.domain.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static s4y.guess.guesser.domain.TestGames.bits;
import static s4y.guess.guesser.domain.TestGames.game;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import s4y.guess.guesser.domain.entities.Game;
import s4y.guess.guesser.domain.vo.Bit;
import s4y.guess.guesser.domain.vo.Guess;
import s4y.guess.guesser.domain.vo.Round;
import s4y.guess.guesser.domain.vo.Rounds;
import s4y.guess.guesser.domain.vo.Score;
import s4y.guess.guesser.domain.vo.WindowSize;

class OperationsTest {

  @Test
  void roundMakeHit() {
    Round round = RoundOperations.make(Bit.ONE, Optional.of(Guess.of(Bit.ONE)));

    assertThat(round).isEqualTo(new Round.Guessed(Bit.ONE, Bit.ONE));
    assertThat(((Round.Guessed) round).hit()).isTrue();
  }

  @Test
  void roundMakeMiss() {
    Round round = RoundOperations.make(Bit.ONE, Optional.of(Guess.of(Bit.ZERO)));

    assertThat(round).isEqualTo(new Round.Guessed(Bit.ONE, Bit.ZERO));
    assertThat(((Round.Guessed) round).hit()).isFalse();
  }

  @Test
  void roundMakeUnguessed() {
    assertThat(RoundOperations.make(Bit.ZERO, Optional.empty()))
        .isEqualTo(new Round.Unguessed(Bit.ZERO));
  }

  @Test
  void scoreOfEmptyRounds() {
    Score score = ScoreOperations.of(Rounds.empty());

    assertThat(score).isEqualTo(new Score(0, 0, 0));
    assertThat(score.unguessed()).isZero();
    assertThat(score.ratio()).isZero();
  }

  @Test
  void scoreCountsHitsGuessedAndTotal() {
    Rounds rounds =
        new Rounds(
            List.of(
                new Round.Unguessed(Bit.ZERO),
                new Round.Guessed(Bit.ONE, Bit.ONE),
                new Round.Guessed(Bit.ONE, Bit.ZERO),
                new Round.Guessed(Bit.ZERO, Bit.ZERO)));

    Score score = ScoreOperations.of(rounds);

    assertThat(score).isEqualTo(new Score(2, 3, 4));
    assertThat(score.unguessed()).isEqualTo(1);
    assertThat(score.ratio()).isEqualTo(2.0 / 3.0);
  }

  @Test
  void scoreRatioIsZeroWhenNothingWasGuessed() {
    Rounds rounds = new Rounds(List.of(new Round.Unguessed(Bit.ONE)));

    Score score = ScoreOperations.of(rounds);

    assertThat(score).isEqualTo(new Score(0, 0, 1));
    assertThat(score.ratio()).isZero();
  }

  @Test
  void gamePressAddsOneRound() {
    Game start = game(3, "01");

    Game next = GameOperations.press(start, Bit.ONE, Optional.of(Guess.of(Bit.ZERO)));

    assertThat(next.id()).isEqualTo(start.id());
    assertThat(next.windowSize()).isEqualTo(start.windowSize());
    assertThat(next.rounds().size()).isEqualTo(3);
    assertThat(next.rounds().last()).contains(new Round.Guessed(Bit.ONE, Bit.ZERO));
    assertThat(start.rounds().size()).isEqualTo(2);
  }

  @Test
  void gamePressWithoutGuessAddsUnguessedRound() {
    Game next = GameOperations.press(game(3, ""), Bit.ONE, Optional.empty());

    assertThat(next.rounds().last()).contains(new Round.Unguessed(Bit.ONE));
  }

  @Test
  void gameResetRemovesRoundsAndKeepsIdAndWindow() {
    Game start = game(5, "0110");

    Game next = GameOperations.reset(start);

    assertThat(next.rounds().size()).isZero();
    assertThat(next.id()).isEqualTo(start.id());
    assertThat(next.windowSize().value()).isEqualTo(5);
  }

  @Test
  void gameWithWindowSizeKeepsRounds() {
    Game start = game(5, "0110");

    Game next = GameOperations.withWindowSize(start, new WindowSize(2));

    assertThat(next.windowSize().value()).isEqualTo(2);
    assertThat(next.rounds()).isEqualTo(start.rounds());
  }

  @Test
  void guessNeedsHistory() {
    assertThat(GuessOperations.needsGuess(bits(""))).isFalse();
    assertThat(GuessOperations.needsGuess(bits("0"))).isTrue();
  }

  @Test
  void guessInputIsTheLastWindowBits() {
    assertThat(GuessOperations.input(game(3, "011010")).asText()).isEqualTo("010");
    assertThat(GuessOperations.input(game(32, "011")).asText()).isEqualTo("011");
    assertThat(GuessOperations.input(game(3, "")).isEmpty()).isTrue();
  }
}
