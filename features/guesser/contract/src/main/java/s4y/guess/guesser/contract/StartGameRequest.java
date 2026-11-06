package s4y.guess.guesser.contract;

/** Body of the request that starts a game. A null window size means the default. */
public record StartGameRequest(Integer windowSize) {}
