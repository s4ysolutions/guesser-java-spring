package s4y.guess.guesser.domain.vo;

/** Counts for a game: hits, rounds with a guess, and all rounds. */
public record Score(int hits, int guessed, int total) {

  /** Rounds without a guess. */
  public int unguessed() {
    return total - guessed;
  }

  /** Share of guesses that were right. It is 0 when there was no guess. */
  public double ratio() {
    return guessed == 0 ? 0 : (double) hits / guessed;
  }
}
