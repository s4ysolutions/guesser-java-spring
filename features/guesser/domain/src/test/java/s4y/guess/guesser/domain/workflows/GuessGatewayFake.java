package s4y.guess.guesser.domain.workflows;

import java.util.ArrayList;
import java.util.List;
import s4y.guess.guesser.domain.errors.GuessFailed;
import s4y.guess.guesser.domain.ports.GuessGateway;
import s4y.guess.guesser.domain.vo.Bits;
import s4y.guess.guesser.domain.vo.Guess;

/** Test fake for {@link GuessGateway}. It answers or fails as told and records its inputs. */
final class GuessGatewayFake implements GuessGateway {

  private final List<Bits> inputs = new ArrayList<>();
  private Guess answer;
  private GuessFailed failure;

  static GuessGatewayFake answering(Guess guess) {
    GuessGatewayFake fake = new GuessGatewayFake();
    fake.answer = guess;
    return fake;
  }

  static GuessGatewayFake failing(GuessFailed failure) {
    GuessGatewayFake fake = new GuessGatewayFake();
    fake.failure = failure;
    return fake;
  }

  List<Bits> inputs() {
    return inputs;
  }

  @Override
  public Guess guess(Bits lastBits) throws GuessFailed {
    inputs.add(lastBits);
    if (failure != null) {
      throw failure;
    }
    return answer;
  }
}
