package s4y.guess.guesser.domain.operations;

import s4y.guess.guesser.domain.vo.Round;
import s4y.guess.guesser.domain.vo.Rounds;
import s4y.guess.guesser.domain.vo.Score;

/** Pure operations on scores. */
public final class ScoreOperations {

  private ScoreOperations() {}

  /** Counts hits, guessed rounds and all rounds. */
  public static Score of(Rounds rounds) {
    int hits = 0;
    int guessed = 0;
    for (Round round : rounds.values()) {
      if (round instanceof Round.Guessed g) {
        guessed++;
        if (g.hit()) {
          hits++;
        }
      }
    }
    return new Score(hits, guessed, rounds.size());
  }
}
