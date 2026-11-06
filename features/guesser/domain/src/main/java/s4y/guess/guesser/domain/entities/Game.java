package s4y.guess.guesser.domain.entities;

import s4y.guess.guesser.domain.vo.GameId;
import s4y.guess.guesser.domain.vo.Rounds;
import s4y.guess.guesser.domain.vo.WindowSize;

/** One game. It is an entity: it is read and written by its id. */
public record Game(GameId id, WindowSize windowSize, Rounds rounds) {}
