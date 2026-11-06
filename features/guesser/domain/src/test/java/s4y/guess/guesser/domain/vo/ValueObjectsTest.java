package s4y.guess.guesser.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static s4y.guess.guesser.domain.TestGames.bits;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import s4y.guess.guesser.domain.errors.InvalidBit;
import s4y.guess.guesser.domain.errors.InvalidGameId;

class ValueObjectsTest {

  @Test
  void bitOfAcceptsZeroAndOne() throws Exception {
    assertThat(Bit.of(0)).isEqualTo(Bit.ZERO);
    assertThat(Bit.of(1)).isEqualTo(Bit.ONE);
    assertThat(Bit.ONE.opposite()).isEqualTo(Bit.ZERO);
    assertThat(Bit.ZERO.value()).isZero();
  }

  @Test
  void bitOfRejectsOtherNumbers() {
    assertThatThrownBy(() -> Bit.of(2)).isInstanceOf(InvalidBit.class);
    assertThatThrownBy(() -> Bit.of(-1)).isInstanceOf(InvalidBit.class);
  }

  @Test
  void lastNTakesTheEndOfTheBits() {
    assertThat(bits("0110").lastN(2).asText()).isEqualTo("10");
    assertThat(bits("0110").lastN(4).asText()).isEqualTo("0110");
    assertThat(bits("0110").lastN(10).asText()).isEqualTo("0110");
  }

  @Test
  void lastNOfZeroOrLessIsEmpty() {
    assertThat(bits("0110").lastN(0).isEmpty()).isTrue();
    assertThat(bits("0110").lastN(-3).isEmpty()).isTrue();
  }

  @Test
  void bitsHaveSizeAndText() {
    assertThat(bits("0110").size()).isEqualTo(4);
    assertThat(Bits.empty().asText()).isEmpty();
  }

  @Test
  void windowSizeClampsIntoRange() {
    assertThat(WindowSize.clamped(0).value()).isEqualTo(1);
    assertThat(WindowSize.clamped(-5).value()).isEqualTo(1);
    assertThat(WindowSize.clamped(1).value()).isEqualTo(1);
    assertThat(WindowSize.clamped(100).value()).isEqualTo(100);
    assertThat(WindowSize.clamped(256).value()).isEqualTo(256);
    assertThat(WindowSize.clamped(1000).value()).isEqualTo(256);
  }

  @Test
  void windowSizeConstructorRejectsOutOfRange() {
    assertThatThrownBy(() -> new WindowSize(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new WindowSize(257)).isInstanceOf(IllegalArgumentException.class);
    assertThat(WindowSize.defaultSize().value()).isEqualTo(32);
  }

  @Test
  void gameIdParsesUuidText() throws Exception {
    UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    assertThat(GameId.parse("123e4567-e89b-12d3-a456-426614174000")).isEqualTo(new GameId(uuid));
    assertThatThrownBy(() -> GameId.parse("nope")).isInstanceOf(InvalidGameId.class);
  }

  @Test
  void guessOutcomeGivesGuessOnlyWhenMade() {
    Guess guess = Guess.of(Bit.ONE);
    assertThat(new GuessOutcome.Made(guess).guess()).contains(guess);
    assertThat(new GuessOutcome.Skipped().guess()).isEmpty();
    assertThat(
            new GuessOutcome.Failed(s4y.guess.guesser.domain.errors.GuessFailed.Reason.TIMEOUT, "x")
                .guess())
        .isEmpty();
  }
}
