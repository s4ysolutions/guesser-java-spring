package s4y.guess.guesser.contract;

import java.util.List;

/** A game as the server sends it: id, window size, all rounds and the score. */
public record GameResponse(String id, int windowSize, List<RoundResponse> rounds, ScoreResponse score) {}
