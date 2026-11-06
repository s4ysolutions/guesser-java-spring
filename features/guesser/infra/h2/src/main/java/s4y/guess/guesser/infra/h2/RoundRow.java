package s4y.guess.guesser.infra.h2;

import jakarta.persistence.Embeddable;

/** One stored round. The guess is null when the round had no guess. */
@Embeddable
public class RoundRow {

  private int bit;
  private Integer guess;

  protected RoundRow() {}

  public RoundRow(int bit, Integer guess) {
    this.bit = bit;
    this.guess = guess;
  }

  public int getBit() {
    return bit;
  }

  public Integer getGuess() {
    return guess;
  }
}
