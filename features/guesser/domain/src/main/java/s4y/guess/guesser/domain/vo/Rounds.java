package s4y.guess.guesser.domain.vo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** All rounds of a game, oldest first. The list cannot be changed. */
public record Rounds(List<Round> values) {

  public Rounds {
    values = List.copyOf(values);
  }

  public static Rounds empty() {
    return new Rounds(List.of());
  }

  /** Returns new rounds with the given round added at the end. */
  public Rounds append(Round round) {
    List<Round> next = new ArrayList<>(values);
    next.add(round);
    return new Rounds(next);
  }

  /** The bits that were pressed, in order. */
  public Bits bits() {
    return new Bits(values.stream().map(Round::bit).toList());
  }

  public int size() {
    return values.size();
  }

  public Optional<Round> last() {
    return values.isEmpty() ? Optional.empty() : Optional.of(values.get(values.size() - 1));
  }
}
