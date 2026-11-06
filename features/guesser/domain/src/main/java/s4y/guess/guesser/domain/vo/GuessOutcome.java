package s4y.guess.guesser.domain.vo;

import java.util.Optional;
import s4y.guess.guesser.domain.errors.GuessFailed;

/** The result of asking for a guess: a guess, a failure, or nothing because there is no history. */
public sealed interface GuessOutcome {

  /** A guess was made. The component is not called guess, because guess() returns an Optional. */
  record Made(Guess value) implements GuessOutcome {}

  /** The gateway failed. The reason and a message tell why. */
  record Failed(GuessFailed.Reason reason, String message) implements GuessOutcome {}

  /** No guess was tried, because there are no earlier bits. */
  record Skipped() implements GuessOutcome {}

  /** The guess, if there is one. */
  default Optional<Guess> guess() {
    return this instanceof Made made ? Optional.of(made.value()) : Optional.empty();
  }
}
