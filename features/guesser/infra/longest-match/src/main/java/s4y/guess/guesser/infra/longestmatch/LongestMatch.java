package s4y.guess.guesser.infra.longestmatch;

import java.util.List;
import java.util.OptionalDouble;
import s4y.guess.guesser.domain.vo.Bit;
import s4y.guess.guesser.domain.vo.Bits;
import s4y.guess.guesser.domain.vo.Guess;

/**
 * The longest-match algorithm. It looks for the longest end of the history that appeared earlier,
 * and predicts the bit that followed it. This class is pure: it has no side effects.
 */
public final class LongestMatch {

  private LongestMatch() {}

  /**
   * Predicts the next bit. Longer matches come first; for the same length the most recent match
   * wins. The confidence is the match length divided by (history length - 1). Without a match
   * the more common bit is chosen, and on a tie the opposite of the last bit. Empty history gives
   * ZERO.
   */
  public static Guess predictNext(Bits history) {
    List<Bit> bits = history.values();
    int length = bits.size();
    if (length == 0) {
      return Guess.of(Bit.ZERO);
    }

    for (int k = length - 1; k >= 1; k--) {
      int suffixStart = length - k;
      for (int s = suffixStart - 1; s >= 0; s--) {
        if (sameRun(bits, s, suffixStart, k)) {
          return new Guess(bits.get(s + k), OptionalDouble.of((double) k / (length - 1)));
        }
      }
    }

    long ones = bits.stream().filter(bit -> bit == Bit.ONE).count();
    long zeros = length - ones;
    if (ones > zeros) {
      return Guess.of(Bit.ONE);
    }
    if (zeros > ones) {
      return Guess.of(Bit.ZERO);
    }
    return Guess.of(bits.get(length - 1).opposite());
  }

  /** True when the k bits at start equal the k bits at from. */
  private static boolean sameRun(List<Bit> bits, int start, int from, int k) {
    for (int i = 0; i < k; i++) {
      if (bits.get(start + i) != bits.get(from + i)) {
        return false;
      }
    }
    return true;
  }
}
