package s4y.guess.guesser.infra.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import s4y.guess.guesser.domain.errors.GuessFailed;
import s4y.guess.guesser.domain.vo.Bit;
import s4y.guess.guesser.domain.vo.Bits;
import s4y.guess.guesser.domain.vo.Guess;

class GuessGatewayScriptedTest {

  private static Bits bits(Bit... values) {
    return new Bits(List.of(values));
  }

  @Test
  void answersInTheOrderOfTheQueue() throws Exception {
    GuessGatewayScripted gateway =
        new GuessGatewayScripted().then(Guess.of(Bit.ONE)).then(Guess.of(Bit.ZERO));

    assertThat(gateway.guess(bits(Bit.ZERO))).isEqualTo(Guess.of(Bit.ONE));
    assertThat(gateway.guess(bits(Bit.ZERO, Bit.ONE))).isEqualTo(Guess.of(Bit.ZERO));
  }

  @Test
  void throwsAQueuedFailure() {
    GuessGatewayScripted gateway =
        new GuessGatewayScripted().thenFail(GuessFailed.Reason.TIMEOUT, "too slow");

    assertThatThrownBy(() -> gateway.guess(bits(Bit.ONE)))
        .isInstanceOfSatisfying(
            GuessFailed.class,
            e -> {
              assertThat(e.reason()).isEqualTo(GuessFailed.Reason.TIMEOUT);
              assertThat(e.getMessage()).isEqualTo("too slow");
            });
  }

  @Test
  void failsWithUnavailableWhenTheQueueIsEmpty() {
    GuessGatewayScripted gateway = new GuessGatewayScripted();

    assertThatThrownBy(() -> gateway.guess(bits(Bit.ONE)))
        .isInstanceOfSatisfying(
            GuessFailed.class,
            e -> {
              assertThat(e.reason()).isEqualTo(GuessFailed.Reason.UNAVAILABLE);
              assertThat(e.getMessage()).isEqualTo("no scripted guess");
            });
  }

  @Test
  void recordsEveryInputIncludingFailedCalls() throws Exception {
    GuessGatewayScripted gateway =
        new GuessGatewayScripted().then(Guess.of(Bit.ONE));
    Bits first = bits(Bit.ZERO);
    Bits second = bits(Bit.ZERO, Bit.ONE);

    gateway.guess(first);
    assertThatThrownBy(() -> gateway.guess(second)).isInstanceOf(GuessFailed.class);

    assertThat(gateway.inputs()).containsExactly(first, second);
  }
}
