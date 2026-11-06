package s4y.guess.guesser.contract;

/** Result of a press: the new round, the guess failure text (or null), and the score. */
public record PressResponse(RoundResponse round, String guessFailure, ScoreResponse score) {}
