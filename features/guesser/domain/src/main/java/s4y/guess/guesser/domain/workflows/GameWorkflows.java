package s4y.guess.guesser.domain.workflows;

import java.util.Optional;
import s4y.guess.core.domain.errors.InfraFailure;
import s4y.guess.core.domain.ports.TransactionRunner;
import s4y.guess.guesser.domain.entities.Game;
import s4y.guess.guesser.domain.errors.ConcurrentPress;
import s4y.guess.guesser.domain.errors.GameNotFound;
import s4y.guess.guesser.domain.errors.GuesserError;
import s4y.guess.guesser.domain.operations.GameOperations;
import s4y.guess.guesser.domain.operations.ScoreOperations;
import s4y.guess.guesser.domain.ports.GameRepository;
import s4y.guess.guesser.domain.ports.GuessGateway;
import s4y.guess.guesser.domain.vo.Bit;
import s4y.guess.guesser.domain.vo.GameId;
import s4y.guess.guesser.domain.vo.GuessOutcome;
import s4y.guess.guesser.domain.vo.Round;
import s4y.guess.guesser.domain.vo.WindowSize;

/**
 * Workflows for games. They own the transaction: each write runs inside
 * {@link TransactionRunner#inTransaction}. Slow work, like asking the gateway, runs before it.
 */
public final class GameWorkflows {

  private GameWorkflows() {}

  /** Creates a new empty game. */
  public static GameSnapshot start(GameRepository games, TransactionRunner tx, WindowSize size)
      throws InfraFailure {
    return tx.inTransaction("start", () -> snapshot(games.create(size)));
  }

  /** Reads a game and its score. This is a read, so there is no transaction. */
  public static GameSnapshot load(GameRepository games, GameId id)
      throws GameNotFound, InfraFailure {
    Game game = games.find(id).orElseThrow(() -> new GameNotFound(id));
    return snapshot(game);
  }

  /**
   * Presses a bit. The guess is made first, from the history before this bit, and outside the
   * transaction. Then the game is read again inside the transaction; if its round count changed,
   * another press happened and this one is refused.
   */
  public static PressOutcome press(
      GameRepository games,
      GuessGateway gateway,
      TransactionRunner tx,
      GameId id,
      Bit bit)
      throws GuesserError, InfraFailure {
    Game before = games.find(id).orElseThrow(() -> new GameNotFound(id));
    GuessOutcome outcome = GuessWorkflows.guessNext(gateway, before);
    return tx.inTransaction(
        "press",
        () -> {
          Game current = games.find(id).orElseThrow(() -> new GameNotFound(id));
          if (current.rounds().size() != before.rounds().size()) {
            throw new ConcurrentPress(id);
          }
          Game next = GameOperations.press(current, bit, outcome.guess());
          games.save(next);
          Round round = next.rounds().last().orElseThrow();
          return new PressOutcome(next, round, failureOf(outcome), ScoreOperations.of(next.rounds()));
        });
  }

  /** Changes the window size of a game. */
  public static GameSnapshot changeWindowSize(
      GameRepository games, TransactionRunner tx, GameId id, WindowSize size)
      throws GuesserError, InfraFailure {
    return tx.inTransaction(
        "changeWindowSize",
        () -> {
          Game current = games.find(id).orElseThrow(() -> new GameNotFound(id));
          Game next = GameOperations.withWindowSize(current, size);
          games.save(next);
          return snapshot(next);
        });
  }

  /** Removes all rounds of a game. */
  public static GameSnapshot reset(GameRepository games, TransactionRunner tx, GameId id)
      throws GuesserError, InfraFailure {
    return tx.inTransaction(
        "reset",
        () -> {
          Game current = games.find(id).orElseThrow(() -> new GameNotFound(id));
          Game next = GameOperations.reset(current);
          games.save(next);
          return snapshot(next);
        });
  }

  private static GameSnapshot snapshot(Game game) {
    return new GameSnapshot(game, ScoreOperations.of(game.rounds()));
  }

  private static Optional<GuessOutcome.Failed> failureOf(GuessOutcome outcome) {
    return outcome instanceof GuessOutcome.Failed failed ? Optional.of(failed) : Optional.empty();
  }
}
