package s4y.guess.guesser.domain.operations;

import s4y.guess.guesser.domain.entities.Game;
import s4y.guess.guesser.domain.vo.Bits;

/** Pure rules about when and from what a guess is made. */
public final class GuessOperations {

  private GuessOperations() {}

  /** A guess needs at least one earlier bit. */
  public static boolean needsGuess(Bits history) {
    return !history.isEmpty();
  }

  /** The bits the guesser may look at: the last window-size bits of the game. */
  public static Bits input(Game game) {
    return game.rounds().bits().lastN(game.windowSize().value());
  }
}
