package s4y.guess.guesser.domain.vo;

import java.util.List;
import java.util.stream.Collectors;

/** An ordered list of bits. The list cannot be changed. */
public record Bits(List<Bit> values) {

  public Bits {
    values = List.copyOf(values);
  }

  public static Bits empty() {
    return new Bits(List.of());
  }

  /** The last n bits. If n is zero or less, the result is empty. */
  public Bits lastN(int n) {
    if (n <= 0) {
      return empty();
    }
    if (n >= values.size()) {
      return this;
    }
    return new Bits(values.subList(values.size() - n, values.size()));
  }

  public int size() {
    return values.size();
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }

  /** The bits as text, for example "0110". */
  public String asText() {
    return values.stream().map(b -> String.valueOf(b.value())).collect(Collectors.joining());
  }
}
