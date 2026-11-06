package s4y.guess.guesser.domain.errors;

/** A text was given as a game id, but it is not a valid id. */
public final class InvalidGameId extends GuesserError {

  private final String raw;

  public InvalidGameId(String raw) {
    super("Not a valid game id: " + raw);
    this.raw = raw;
  }

  public String raw() {
    return raw;
  }
}
