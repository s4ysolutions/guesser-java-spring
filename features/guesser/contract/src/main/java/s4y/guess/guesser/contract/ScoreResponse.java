package s4y.guess.guesser.contract;

/** Counts for a game: hits, rounds with a guess, all rounds, rounds without a guess, hit ratio. */
public record ScoreResponse(int hits, int guessed, int total, int unguessed, double ratio) {}
