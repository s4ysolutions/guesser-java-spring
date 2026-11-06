package s4y.guess.guesser.domain.workflows;

import java.util.Optional;
import s4y.guess.guesser.domain.entities.Game;
import s4y.guess.guesser.domain.vo.GuessOutcome;
import s4y.guess.guesser.domain.vo.Round;
import s4y.guess.guesser.domain.vo.Score;

/** The result of one press: the new game, the new round, the guess failure if any, the score. */
public record PressOutcome(
    Game game, Round round, Optional<GuessOutcome.Failed> guessFailure, Score score) {}
