package s4y.guess.guesser.domain.errors;

import s4y.guess.guesser.domain.vo.GameId;

/** The game changed while a press was in progress, so the press was not stored. */
public final class ConcurrentPress extends GuesserError {

  private final GameId id;

  public ConcurrentPress(GameId id) {
    super("Game changed during the press: " + id.value());
    this.id = id;
  }

  public GameId id() {
    return id;
  }
}
