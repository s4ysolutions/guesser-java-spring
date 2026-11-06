package s4y.guess.guesser.infra.longestmatch;

import s4y.guess.guesser.domain.ports.GuessGateway;
import s4y.guess.guesser.domain.vo.Bits;
import s4y.guess.guesser.domain.vo.Guess;

/** {@link GuessGateway} that uses {@link LongestMatch}. It never fails. */
public final class GuessGatewayLongestMatch implements GuessGateway {

  @Override
  public Guess guess(Bits lastBits) {
    return LongestMatch.predictNext(lastBits);
  }
}
