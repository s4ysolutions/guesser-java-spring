package s4y.guess.guesser.domain.vo;

import java.util.OptionalDouble;

/** A guess for the next bit, with an optional confidence. */
public record Guess(Bit bit, OptionalDouble confidence) {

  public static Guess of(Bit bit) {
    return new Guess(bit, OptionalDouble.empty());
  }
}
