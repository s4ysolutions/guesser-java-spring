package s4y.guess.guesser.domain.vo;

/** How many of the last bits the guesser may look at. It is always between MIN and MAX. */
public record WindowSize(int value) {

  public static final int MIN = 1;
  public static final int MAX = 256;
  public static final int DEFAULT_VALUE = 32;

  public WindowSize {
    if (value < MIN || value > MAX) {
      throw new IllegalArgumentException(
          "Window size must be between " + MIN + " and " + MAX + ": " + value);
    }
  }

  /** Moves a requested size into the allowed range. */
  public static WindowSize clamped(int requested) {
    return new WindowSize(Math.max(MIN, Math.min(MAX, requested)));
  }

  public static WindowSize defaultSize() {
    return new WindowSize(DEFAULT_VALUE);
  }
}
