package s4y.guess.guesser.contract;

/** One round. Guess and hit are null when the server made no guess. */
public record RoundResponse(int bit, Integer guess, Boolean hit) {}
