package s4y.guess.guesser.domain.ports;

import s4y.guess.guesser.domain.errors.GuessFailed;
import s4y.guess.guesser.domain.vo.Bits;
import s4y.guess.guesser.domain.vo.Guess;

/** Port that guesses the next bit from the last bits. */
public interface GuessGateway {

  Guess guess(Bits lastBits) throws GuessFailed;
}
