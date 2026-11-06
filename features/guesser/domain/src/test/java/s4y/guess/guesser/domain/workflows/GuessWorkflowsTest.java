package s4y.guess.guesser.domain.workflows;

import static org.assertj.core.api.Assertions.assertThat;
import static s4y.guess.guesser.domain.TestGames.game;

import org.junit.jupiter.api.Test;
import s4y.guess.guesser.domain.errors.GuessFailed;
import s4y.guess.guesser.domain.vo.Bit;
import s4y.guess.guesser.domain.vo.Guess;
import s4y.guess.guesser.domain.vo.GuessOutcome;

class GuessWorkflowsTest {

  @Test
  void skipsWithoutHistoryAndDoesNotCallTheGateway() {
    GuessGatewayFake gateway = GuessGatewayFake.answering(Guess.of(Bit.ONE));

    GuessOutcome outcome = GuessWorkflows.guessNext(gateway, game(3, ""));

    assertThat(outcome).isEqualTo(new GuessOutcome.Skipped());
    assertThat(gateway.inputs()).isEmpty();
  }

  @Test
  void makesAGuessFromTheLastWindowBits() {
    GuessGatewayFake gateway = GuessGatewayFake.answering(Guess.of(Bit.ONE));

    GuessOutcome outcome = GuessWorkflows.guessNext(gateway, game(2, "0110"));

    assertThat(outcome).isEqualTo(new GuessOutcome.Made(Guess.of(Bit.ONE)));
    assertThat(gateway.inputs()).hasSize(1);
    assertThat(gateway.inputs().get(0).asText()).isEqualTo("10");
  }

  @Test
  void turnsGuessFailedIntoFailedOutcome() {
    GuessGatewayFake gateway =
        GuessGatewayFake.failing(new GuessFailed(GuessFailed.Reason.BAD_ANSWER, "no 0 or 1"));

    GuessOutcome outcome = GuessWorkflows.guessNext(gateway, game(2, "01"));

    assertThat(outcome)
        .isEqualTo(new GuessOutcome.Failed(GuessFailed.Reason.BAD_ANSWER, "no 0 or 1"));
  }
}
