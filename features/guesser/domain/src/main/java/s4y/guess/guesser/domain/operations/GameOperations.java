package s4y.guess.guesser.domain.operations;

import java.util.Optional;
import s4y.guess.guesser.domain.entities.Game;
import s4y.guess.guesser.domain.vo.Bit;
import s4y.guess.guesser.domain.vo.Guess;
import s4y.guess.guesser.domain.vo.Rounds;
import s4y.guess.guesser.domain.vo.WindowSize;

/** Pure operations on games. Each one returns a new game. */
public final class GameOperations {

  private GameOperations() {}

  /** Adds a round for the pressed bit and the guess that was made before it. */
  public static Game press(Game game, Bit bit, Optional<Guess> guess) {
    return new Game(
        game.id(), game.windowSize(), game.rounds().append(RoundOperations.make(bit, guess)));
  }

  /** Removes all rounds. Id and window size stay. */
  public static Game reset(Game game) {
    return new Game(game.id(), game.windowSize(), Rounds.empty());
  }

  public static Game withWindowSize(Game game, WindowSize size) {
    return new Game(game.id(), size, game.rounds());
  }
}
