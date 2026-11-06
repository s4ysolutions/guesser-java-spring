package s4y.guess.guesser.domain.ports;

import java.util.Optional;
import s4y.guess.core.domain.errors.InfraFailure;
import s4y.guess.guesser.domain.entities.Game;
import s4y.guess.guesser.domain.errors.ConcurrentPress;
import s4y.guess.guesser.domain.vo.GameId;
import s4y.guess.guesser.domain.vo.WindowSize;

/** Port to create, find and save games. */
public interface GameRepository {

  /** Creates an empty game. The adapter makes the id. */
  Game create(WindowSize size) throws InfraFailure;

  Optional<Game> find(GameId id) throws InfraFailure;

  /**
   * Saves a game. It fails with {@link ConcurrentPress} when another transaction changed the
   * same game after this transaction read it.
   */
  void save(Game game) throws ConcurrentPress, InfraFailure;
}
