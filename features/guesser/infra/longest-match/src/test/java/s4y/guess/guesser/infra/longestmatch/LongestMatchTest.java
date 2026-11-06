package s4y.guess.guesser.infra.longestmatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import s4y.guess.guesser.domain.vo.Bit;
import s4y.guess.guesser.domain.vo.Bits;
import s4y.guess.guesser.domain.vo.Guess;

class LongestMatchTest {

  private static Bits bits(String text) {
    return new Bits(text.chars().mapToObj(c -> c == '1' ? Bit.ONE : Bit.ZERO).toList());
  }

  @Test
  void alternating() {
    Guess guess = LongestMatch.predictNext(bits("0101010"));

    assertThat(guess.bit()).isEqualTo(Bit.ONE);
    assertThat(guess.confidence().orElseThrow()).isCloseTo(5.0 / 6, within(1e-12));
  }

  @Test
  void repeatedBlock() {
    Guess guess = LongestMatch.predictNext(bits("00110011001"));

    assertThat(guess.bit()).isEqualTo(Bit.ONE);
    assertThat(guess.confidence().orElseThrow()).isCloseTo(7.0 / 10, within(1e-12));
  }

  @Test
  void mostRecentMatchWins() {
    Guess guess = LongestMatch.predictNext(bits("1011"));

    assertThat(guess.bit()).isEqualTo(Bit.ONE);
    assertThat(guess.confidence().orElseThrow()).isCloseTo(1.0 / 3, within(1e-12));
  }

  @Test
  void noMatchUsesTheMajorityBitWithoutConfidence() {
    Guess guess = LongestMatch.predictNext(bits("1"));

    assertThat(guess).isEqualTo(Guess.of(Bit.ONE));
    assertThat(guess.confidence()).isEmpty();
  }

  @Test
  void noMatchAndATieUsesTheOppositeOfTheLastBit() {
    Guess first = LongestMatch.predictNext(bits("01"));
    Guess second = LongestMatch.predictNext(bits("10"));

    assertThat(first).isEqualTo(Guess.of(Bit.ZERO));
    assertThat(first.confidence()).isEmpty();
    assertThat(second).isEqualTo(Guess.of(Bit.ONE));
    assertThat(second.confidence()).isEmpty();
  }

  @Test
  void emptyInputGivesZero() {
    assertThat(LongestMatch.predictNext(Bits.empty())).isEqualTo(Guess.of(Bit.ZERO));
  }
}
