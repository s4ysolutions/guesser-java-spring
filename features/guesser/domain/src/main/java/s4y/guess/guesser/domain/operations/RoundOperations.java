package s4y.guess.guesser.domain.operations;

import java.util.Optional;
import s4y.guess.guesser.domain.vo.Bit;
import s4y.guess.guesser.domain.vo.Guess;
import s4y.guess.guesser.domain.vo.Round;

/** Pure operations on rounds. */
public final class RoundOperations {

  private RoundOperations() {}

  /** Makes a round from the pressed bit and the guess that was made before it, if any. */
  public static Round make(Bit bit, Optional<Guess> guess) {
    return guess
        .<Round>map(g -> new Round.Guessed(bit, g.bit()))
        .orElseGet(() -> new Round.Unguessed(bit));
  }
}
