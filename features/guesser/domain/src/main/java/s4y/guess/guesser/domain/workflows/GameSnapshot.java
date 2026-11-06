package s4y.guess.guesser.domain.workflows;

import s4y.guess.guesser.domain.entities.Game;
import s4y.guess.guesser.domain.vo.Score;

/** The result of loading or changing a game: the game together with its score. */
public record GameSnapshot(Game game, Score score) {}
