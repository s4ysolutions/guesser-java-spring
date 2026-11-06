package s4y.guess.guesser.domain.errors;

/** A number was given as a bit, but it is not 0 or 1. */
public final class InvalidBit extends GuesserError {

  private final int value;

  public InvalidBit(int value) {
    super("Not a bit (use 0 or 1): " + value);
    this.value = value;
  }

  public int value() {
    return value;
  }
}
