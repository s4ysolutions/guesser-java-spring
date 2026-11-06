package s4y.guess.guesser.domain.vo;

import java.util.UUID;
import s4y.guess.guesser.domain.errors.InvalidGameId;

/** The id of a game. */
public record GameId(UUID value) {

  /** Reads an id from text. A text that is not a UUID is an error. */
  public static GameId parse(String text) throws InvalidGameId {
    if (text == null) {
      throw new InvalidGameId("null");
    }
    try {
      return new GameId(UUID.fromString(text));
    } catch (IllegalArgumentException e) {
      throw new InvalidGameId(text);
    }
  }
}
