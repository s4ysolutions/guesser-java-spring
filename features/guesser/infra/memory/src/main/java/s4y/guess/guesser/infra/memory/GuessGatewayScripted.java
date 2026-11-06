package s4y.guess.guesser.infra.memory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import s4y.guess.guesser.domain.errors.GuessFailed;
import s4y.guess.guesser.domain.ports.GuessGateway;
import s4y.guess.guesser.domain.vo.Bits;
import s4y.guess.guesser.domain.vo.Guess;

/**
 * Test and demo adapter for {@link GuessGateway}. It answers from a queue of guesses and
 * failures, in the order they were added, and records the bits it received. When the queue is
 * empty it fails with UNAVAILABLE.
 */
public final class GuessGatewayScripted implements GuessGateway {

  /** One queued answer: a guess or a failure. */
  private sealed interface Step {
    record Answer(Guess guess) implements Step {}

    record Failure(GuessFailed failure) implements Step {}
  }

  private final Deque<Step> queue = new ArrayDeque<>();
  private final List<Bits> inputs = new ArrayList<>();

  /** Queues a guess for the next call. */
  public synchronized GuessGatewayScripted then(Guess guess) {
    queue.addLast(new Step.Answer(guess));
    return this;
  }

  /** Queues a failure for the next call. */
  public synchronized GuessGatewayScripted thenFail(GuessFailed.Reason reason, String message) {
    queue.addLast(new Step.Failure(new GuessFailed(reason, message)));
    return this;
  }

  /** The bits received so far, one entry per call, oldest first. */
  public synchronized List<Bits> inputs() {
    return List.copyOf(inputs);
  }

  @Override
  public synchronized Guess guess(Bits lastBits) throws GuessFailed {
    inputs.add(lastBits);
    Step step = queue.pollFirst();
    if (step == null) {
      throw new GuessFailed(GuessFailed.Reason.UNAVAILABLE, "no scripted guess");
    }
    return switch (step) {
      case Step.Answer answer -> answer.guess();
      case Step.Failure failure -> throw failure.failure();
    };
  }
}
