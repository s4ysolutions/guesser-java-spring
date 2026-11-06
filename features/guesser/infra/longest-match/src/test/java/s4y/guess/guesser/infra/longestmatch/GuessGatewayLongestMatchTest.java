package s4y.guess.guesser.infra.longestmatch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import s4y.guess.guesser.domain.vo.Bit;
import s4y.guess.guesser.domain.vo.Bits;

class GuessGatewayLongestMatchTest {

  @Test
  void neverFailsAndAnswersLikeTheAlgorithm() {
    Bits history = new Bits(java.util.List.of(Bit.ZERO, Bit.ONE, Bit.ZERO));

    assertThat(new GuessGatewayLongestMatch().guess(history))
        .isEqualTo(LongestMatch.predictNext(history));
  }
}
