package s4y.guess.guesser.domain.workflows;

import s4y.guess.guesser.domain.entities.Game;
import s4y.guess.guesser.domain.errors.GuessFailed;
import s4y.guess.guesser.domain.operations.GuessOperations;
import s4y.guess.guesser.domain.ports.GuessGateway;
import s4y.guess.guesser.domain.vo.Bits;
import s4y.guess.guesser.domain.vo.GuessOutcome;

/** Workflows that ask the gateway for a guess. They have side effects through the port. */
public final class GuessWorkflows {

  private GuessWorkflows() {}

  /**
   * Guesses the next bit from the game history. Without history the gateway is not called and
   * the result is Skipped. A gateway failure becomes a Failed outcome.
   */
  public static GuessOutcome guessNext(GuessGateway gateway, Game game) {
    Bits input = GuessOperations.input(game);
    if (!GuessOperations.needsGuess(input)) {
      return new GuessOutcome.Skipped();
    }
    try {
      return new GuessOutcome.Made(gateway.guess(input));
    } catch (GuessFailed e) {
      return new GuessOutcome.Failed(e.reason(), e.getMessage());
    }
  }
}
