package s4y.guess.guesser.domain.errors;

import s4y.guess.guesser.domain.vo.GameId;

/** There is no game with the given id. */
public final class GameNotFound extends GuesserError {

  private final GameId id;

  public GameNotFound(GameId id) {
    super("Game not found: " + id.value());
    this.id = id;
  }

  public GameId id() {
    return id;
  }
}
