package s4y.guess.guesser.domain.vo;

/** One round of the game: the bit the person pressed, and the guess if there was one. */
public sealed interface Round permits Round.Guessed, Round.Unguessed {

  Bit bit();

  /** A round where the guesser made a guess. */
  record Guessed(Bit bit, Bit guess) implements Round {
    public boolean hit() {
      return bit == guess;
    }
  }

  /** A round without a guess, for example the first round or a failed guess. */
  record Unguessed(Bit bit) implements Round {}
}
