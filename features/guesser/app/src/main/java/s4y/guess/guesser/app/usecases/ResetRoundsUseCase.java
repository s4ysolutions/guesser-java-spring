package s4y.guess.guesser.app.usecases;

import s4y.guess.core.domain.errors.InfraFailure;
import s4y.guess.core.domain.ports.TransactionRunner;
import s4y.guess.guesser.domain.errors.GuesserError;
import s4y.guess.guesser.domain.ports.GameRepository;
import s4y.guess.guesser.domain.vo.GameId;
import s4y.guess.guesser.domain.workflows.GameSnapshot;
import s4y.guess.guesser.domain.workflows.GameWorkflows;

/** Removes all rounds of a game. The window size stays. */
public final class ResetRoundsUseCase {

  /** The id of the game to reset. */
  public record Command(GameId id) {}

  private final GameRepository games;
  private final TransactionRunner tx;

  public ResetRoundsUseCase(GameRepository games, TransactionRunner tx) {
    this.games = games;
    this.tx = tx;
  }

  public GameSnapshot execute(Command command) throws GuesserError, InfraFailure {
    return GameWorkflows.reset(games, tx, command.id());
  }
}
